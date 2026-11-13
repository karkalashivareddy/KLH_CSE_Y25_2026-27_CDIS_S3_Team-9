package com.cloudresilience.modules.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.cloudresilience.modules.events.application.DeadLetterService;
import com.cloudresilience.modules.events.application.EventEnvelopeCodec;
import com.cloudresilience.modules.events.application.OutboxPublisher;
import com.cloudresilience.modules.events.application.OutboxWriter;
import com.cloudresilience.modules.events.domain.EventEnvelope;
import com.cloudresilience.modules.events.domain.EventType;
import com.cloudresilience.modules.events.domain.OrderEventPayloads;
import com.cloudresilience.modules.events.messaging.EventPublisher;
import com.cloudresilience.modules.events.messaging.EventTopology;
import com.cloudresilience.modules.events.persistence.OutboxRepository;
import com.cloudresilience.modules.orders.api.CheckoutRequest;
import com.cloudresilience.modules.orders.application.CheckoutService;
import com.cloudresilience.modules.orders.domain.OrderStatus;
import com.cloudresilience.modules.orders.domain.PaymentStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real PostgreSQL and RabbitMQ verification for outbox publication, consumer
 * idempotency, bounded retry, dead-lettering, replay and publisher recovery.
 * Retry tiers are shortened so the test remains deterministic and fast.
 */
@SpringBootTest(properties = {
        "app.outbox.publisher.enabled=true",
        "app.messaging.enabled=true",
        "app.messaging.retry-tiers-ms=300,600,1200",
        "app.outbox.publisher.interval-ms=3600000",
        "app.outbox.publisher.initial-delay-ms=3600000"})
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "runIntegrationTests", matches = "true")
class EventDrivenIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine3.22");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.4.0-alpine3.22").withExposedPorts(6379);
    @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.0-management-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        p.add("spring.datasource.username", POSTGRES::getUsername);
        p.add("spring.datasource.password", POSTGRES::getPassword);
        p.add("spring.data.redis.host", REDIS::getHost);
        p.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        p.add("spring.rabbitmq.host", RABBIT::getHost);
        p.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        p.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        p.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CheckoutService checkout;
    @Autowired OutboxWriter writer;
    @Autowired OutboxRepository repository;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired EventPublisher eventPublisher;
    @Autowired EventEnvelopeCodec codec;
    @Autowired DeadLetterService deadLetters;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired AmqpAdmin amqpAdmin;

    private UUID customer;
    private UUID admin;
    private UUID product;
    private UUID category;
    private UUID cartId;

    @BeforeEach
    void fixture() {
        // Each test owns the event tables; consumers may still be draining the
        // previous test's queues, so assertions are also filtered by new IDs.
        jdbc.update("DELETE FROM dead_letter_replays");
        jdbc.update("DELETE FROM dead_letter_messages");
        jdbc.update("DELETE FROM order_activity");
        jdbc.update("DELETE FROM in_app_notifications");
        jdbc.update("DELETE FROM processed_events");
        jdbc.update("DELETE FROM outbox_events");
        customer = createUser("event-buyer", "CUSTOMER");
        admin = createUser("event-admin", "ADMIN");
        category = UUID.randomUUID();
        product = UUID.randomUUID();
        cartId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO categories(id,name,slug,active) VALUES(?,?,?,true)", category, "Event test", "event-test-" + suffix);
        jdbc.update("INSERT INTO products(id,sku,name,slug,price,currency,category_id,status) VALUES(?,?,?,?,?,'USD',?,'ACTIVE')",
                product, "EVT-" + suffix.toUpperCase(), "Event lamp", "event-lamp-" + suffix, new BigDecimal("15.00"), category);
        jdbc.update("INSERT INTO inventory(id,product_id,quantity_on_hand,quantity_reserved) VALUES(?,?,50,0)", UUID.randomUUID(), product);
        jdbc.update("INSERT INTO carts(id,user_id,status) VALUES(?,?,'ACTIVE')", cartId, customer);
        jdbc.update("INSERT INTO cart_items(id,cart_id,product_id,quantity) VALUES(?,?,?,2)", UUID.randomUUID(), cartId, product);
    }

    @Test
    void checkoutEventIsPublishedAndConsumedByBothProjections() {
        UUID orderId = checkoutOrder("event-publish-0001");

        assertThat(outboxPublisher.publishDueOnce()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(count("SELECT count(*) FROM in_app_notifications WHERE order_id=?", orderId)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM order_activity WHERE order_id=?", orderId)).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE aggregate_id=?", String.class, orderId))
                .isEqualTo("PUBLISHED");
        assertThat(outboxPublisher.publishDueOnce()).isZero();
        assertThat(count("SELECT count(*) FROM in_app_notifications WHERE order_id=?", orderId)).isEqualTo(1);
    }

    @Test
    void duplicateDeliveryProducesOneLogicalEffectPerConsumer() {
        UUID orderId = checkoutOrder("event-duplicate-0001");
        assertThat(outboxPublisher.publishDueOnce()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(20)).until(() -> queueDepth(EventTopology.NOTIFICATION_QUEUE) == 0
                && queueDepth(EventTopology.ACTIVITY_QUEUE) == 0);

        UUID eventId = jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregate_id=?", UUID.class, orderId);
        String payload = jdbc.queryForObject("SELECT payload::text FROM outbox_events WHERE id=?", String.class, eventId);
        eventPublisher.publish(payload.getBytes(StandardCharsets.UTF_8), eventId, EventType.ORDER_PLACED.wireName(), 1,
                orderId, EventType.ORDER_PLACED.routingKey());
        eventPublisher.publish(payload.getBytes(StandardCharsets.UTF_8), eventId, EventType.ORDER_PLACED.wireName(), 1,
                orderId, EventType.ORDER_PLACED.routingKey());

        await().atMost(Duration.ofSeconds(20)).until(() -> queueDepth(EventTopology.NOTIFICATION_QUEUE) == 0
                && queueDepth(EventTopology.ACTIVITY_QUEUE) == 0);
        assertThat(count("SELECT count(*) FROM in_app_notifications WHERE order_id=?", orderId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM order_activity WHERE order_id=?", orderId)).isEqualTo(1);
    }

    @Test
    void failingConsumerRetriesThenQuarantinesWithoutAcknowledgingUncommittedWork() {
        UUID orderId = checkoutOrder("event-poison-0001");
        await().atMost(Duration.ofSeconds(20)).until(() -> queueDepth(EventTopology.NOTIFICATION_QUEUE) == 0
                && queueDepth(EventTopology.ACTIVITY_QUEUE) == 0);
        UUID invalidCustomer = UUID.randomUUID();
        writer.append(EventType.ORDER_PLACED, orderId, OrderEventPayloads.order(orderId, invalidCustomer,
                "CR-POISON", OrderStatus.CONFIRMED, PaymentStatus.SUCCEEDED, new BigDecimal("30.00"), "USD", 2),
                orderId, null);
        assertThat(outboxPublisher.publishDueOnce()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM dead_letter_messages
                WHERE consumer_name=? AND error_category='CONSUMER_ERROR'
                """, Integer.class, EventTopology.NOTIFICATION_CONSUMER)).isEqualTo(1));
        UUID poisonEventId = jdbc.queryForObject("""
                SELECT event_id FROM dead_letter_messages WHERE consumer_name=? ORDER BY created_at DESC LIMIT 1
                """, UUID.class, EventTopology.NOTIFICATION_CONSUMER);
        assertThat(count("SELECT count(*) FROM processed_events WHERE event_id=? AND consumer_name=?",
                poisonEventId, EventTopology.NOTIFICATION_CONSUMER)).isZero();
        assertThat(count("SELECT count(*) FROM order_activity WHERE event_id=?", poisonEventId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM in_app_notifications WHERE event_id=?", poisonEventId)).isZero();
    }

    @Test
    void unsupportedEventVersionIsQuarantinedImmediately() {
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), EventType.ORDER_PLACED.wireName(), 2,
                "CustomerOrder", UUID.randomUUID(), java.time.Instant.now(), null, null,
                java.util.Map.of("orderId", UUID.randomUUID().toString(), "customerId", customer.toString(),
                        "orderNumber", "CR-V2", "status", "CONFIRMED"));
        rabbitTemplate.send(EventTopology.EVENTS_EXCHANGE, EventType.ORDER_PLACED.routingKey(), new org.springframework.amqp.core.Message(
                codec.encode(envelope), new org.springframework.amqp.core.MessageProperties()));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM dead_letter_messages WHERE event_id=? AND error_category='UNSUPPORTED_VERSION'
                """, Integer.class, envelope.eventId())).isGreaterThanOrEqualTo(1));
        assertThat(count("SELECT count(*) FROM processed_events WHERE event_id=?", envelope.eventId())).isZero();
    }

    @Test
    void publisherCrashAfterBrokerConfirmationIsRecoveredWithoutDuplicateEffects() {
        UUID orderId = checkoutOrder("event-crash-0001");
        var claimed = repository.claimDue(1, "crashed-publisher", 30);
        assertThat(claimed).hasSize(1);
        eventPublisher.publish(claimed.getFirst(), EventType.ORDER_PLACED.routingKey());
        // Simulate a crash: the broker accepted the message but the row was not finalized.
        jdbc.update("UPDATE outbox_events SET lease_expires_at=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE aggregate_id=?",
                orderId);

        assertThat(outboxPublisher.publishDueOnce()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(count("SELECT count(*) FROM in_app_notifications WHERE order_id=?", orderId)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM order_activity WHERE order_id=?", orderId)).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE aggregate_id=?", String.class, orderId))
                .isEqualTo("PUBLISHED");
    }

    @Test
    void controlledDeadLetterReplayIsAuditableAndIdempotent() {
        UUID orderId = checkoutOrder("event-replay-0001");
        UUID eventId = jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregate_id=?", UUID.class, orderId);
        String payload = jdbc.queryForObject("SELECT payload::text FROM outbox_events WHERE id=?", String.class, eventId);
        EventEnvelope envelope = codec.decode(payload.getBytes(StandardCharsets.UTF_8));
        deadLetters.quarantine(envelope, EventType.ORDER_PLACED.routingKey(), EventTopology.NOTIFICATION_CONSUMER,
                "TEST_QUARANTINE");
        UUID deadLetterId = jdbc.queryForObject("SELECT id FROM dead_letter_messages WHERE event_id=? AND consumer_name=?",
                UUID.class, eventId, EventTopology.NOTIFICATION_CONSUMER);

        deadLetters.replay(deadLetterId, admin, "Replay after operator verification");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(count("SELECT count(*) FROM in_app_notifications WHERE order_id=?", orderId)).isEqualTo(1));
        assertThat(jdbc.queryForObject("SELECT status FROM dead_letter_messages WHERE id=?", String.class, deadLetterId))
                .isEqualTo("REPLAYED");
        assertThat(count("SELECT count(*) FROM dead_letter_replays WHERE dead_letter_id=?", deadLetterId)).isEqualTo(1);
    }

    private UUID checkoutOrder(String key) {
        checkout.checkout(customer, key, new CheckoutRequest(List.of(new CheckoutRequest.Line(product, 2))));
        return jdbc.queryForObject("SELECT id FROM customer_orders WHERE customer_id=? AND idempotency_key=?",
                UUID.class, customer, key);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private long queueDepth(String queue) {
        var information = amqpAdmin.getQueueInfo(queue);
        return information == null ? -1 : information.getMessageCount();
    }

    private UUID createUser(String label, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,first_name,last_name,enabled,account_locked) VALUES(?,?,?,'Test','User',true,false)",
                id, label + "-" + id + "@example.test", "unused-test-hash");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name=?", id, role);
        return id;
    }
}
