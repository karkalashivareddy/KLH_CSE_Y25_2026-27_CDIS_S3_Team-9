package com.cloudresilience.modules.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.cloudresilience.modules.events.application.OutboxPublisher;
import com.cloudresilience.modules.events.application.OutboxWriter;
import com.cloudresilience.modules.events.domain.EventType;
import com.cloudresilience.modules.events.domain.OrderEventPayloads;
import com.cloudresilience.modules.events.messaging.EventPublisher;
import com.cloudresilience.modules.events.persistence.OutboxRepository;
import com.cloudresilience.modules.orders.api.CheckoutRequest;
import com.cloudresilience.modules.orders.application.CheckoutService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies outbox lease, claim and failure state against real PostgreSQL.
 * The broker boundary is a mock so broker-outage behavior is deterministic and
 * the test does not require a RabbitMQ container.
 */
@SpringBootTest(properties = {"app.messaging.enabled=false", "app.outbox.publisher.enabled=true",
        "app.outbox.publisher.interval-ms=3600000", "app.outbox.publisher.initial-delay-ms=3600000"})
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "runIntegrationTests", matches = "true")
class OutboxLeaseIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine3.22");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.4.0-alpine3.22").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        p.add("spring.datasource.username", POSTGRES::getUsername);
        p.add("spring.datasource.password", POSTGRES::getPassword);
        p.add("spring.data.redis.host", REDIS::getHost);
        p.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @MockitoBean EventPublisher eventPublisher;

    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxWriter writer;
    @Autowired OutboxRepository repository;
    @Autowired OutboxPublisher outboxPublisher;
    @Autowired CheckoutService checkout;

    private UUID customer;
    private UUID product;
    private UUID category;
    private UUID cartId;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM dead_letter_replays");
        jdbc.update("DELETE FROM dead_letter_messages");
        jdbc.update("DELETE FROM order_activity");
        jdbc.update("DELETE FROM in_app_notifications");
        jdbc.update("DELETE FROM processed_events");
        jdbc.update("DELETE FROM outbox_events");
        customer = createUser("outbox");
        category = UUID.randomUUID();
        product = UUID.randomUUID();
        cartId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO categories(id,name,slug,active) VALUES(?,?,?,true)", category, "Outbox test", "outbox-test-" + suffix);
        jdbc.update("INSERT INTO products(id,sku,name,slug,price,currency,category_id,status) VALUES(?,?,?,?,?,'USD',?,'ACTIVE')",
                product, "OUT-" + suffix.toUpperCase(), "Outbox lamp", "outbox-lamp-" + suffix, new BigDecimal("10.00"), category);
        jdbc.update("INSERT INTO inventory(id,product_id,quantity_on_hand,quantity_reserved) VALUES(?,?,20,0)", UUID.randomUUID(), product);
        jdbc.update("INSERT INTO carts(id,user_id,status) VALUES(?,?,'ACTIVE')", cartId, customer);
        jdbc.update("INSERT INTO cart_items(id,cart_id,product_id,quantity) VALUES(?,?,?,1)", UUID.randomUUID(), cartId, product);
    }

    @Test
    void successfulBrokerConfirmationMarksTheEventPublished() {
        checkout.checkout(customer, "outbox-publish-0001", request());
        UUID orderId = jdbc.queryForObject("SELECT id FROM customer_orders WHERE customer_id=?", UUID.class, customer);

        assertThat(outboxPublisher.publishDueOnce()).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE aggregate_id=?", String.class, orderId))
                .isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM outbox_events WHERE aggregate_id=?",
                Boolean.class, orderId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM outbox_events WHERE aggregate_id=?", Integer.class, orderId))
                .isZero();
    }

    @Test
    void brokerFailureLeavesACommittedEventPendingAndSchedulesBackoff() {
        checkout.checkout(customer, "outbox-backoff-0001", request());
        UUID orderId = jdbc.queryForObject("SELECT id FROM customer_orders WHERE customer_id=?", UUID.class, customer);
        doThrow(new EventPublisher.EventPublishException("Publisher confirm failed"))
                .when(eventPublisher).publish(any(), anyString());

        assertThat(outboxPublisher.publishDueOnce()).isZero();

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT status,attempt_count,last_error_category,next_attempt_at > CURRENT_TIMESTAMP AS backed_off
                FROM outbox_events WHERE aggregate_id=?
                """, orderId);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("attempt_count")).intValue()).isEqualTo(1);
        assertThat(row.get("last_error_category")).isEqualTo("CONFIRM_TIMEOUT");
        assertThat(row.get("backed_off")).isEqualTo(true);
        // The backoff delays the next attempt, so an immediate pass cannot re-publish.
        assertThat(outboxPublisher.publishDueOnce()).isZero();
    }

    @Test
    void concurrentPublishersCannotClaimTheSameActiveLease() throws Exception {
        UUID eventId = appendEvent();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> claimAfterBarrier("owner-a", ready, start));
            var second = pool.submit(() -> claimAfterBarrier("owner-b", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int claimed = first.get(10, TimeUnit.SECONDS).size() + second.get(10, TimeUnit.SECONDS).size();
            assertThat(claimed).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE id=?", String.class, eventId))
                .isEqualTo("PROCESSING");
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT lease_owner) FROM outbox_events WHERE id=?",
                Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void expiredLeaseIsReclaimedSoACrashedPublisherCannotStrandEvents() {
        UUID eventId = appendEvent();
        var firstClaim = repository.claimDue(1, "crashed-owner", 30);
        assertThat(firstClaim).hasSize(1);
        jdbc.update("UPDATE outbox_events SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id=?", eventId);

        var reclaimed = repository.claimDue(1, "recovery-owner", 30);

        assertThat(reclaimed).hasSize(1);
        assertThat(reclaimed.getFirst().id()).isEqualTo(eventId);
        assertThat(jdbc.queryForObject("SELECT lease_owner FROM outbox_events WHERE id=?", String.class, eventId))
                .isEqualTo("recovery-owner");
    }

    private List<com.cloudresilience.modules.events.persistence.OutboxEvent> claimAfterBarrier(String owner,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("barrier timed out");
        return repository.claimDue(10, owner, 30);
    }

    private UUID appendEvent() {
        UUID orderId = UUID.randomUUID();
        writer.append(EventType.ORDER_CANCELLED, orderId, OrderEventPayloads.cancellation(orderId, customer,
                "CR-TEST", new BigDecimal("10.00"), "USD"), orderId, null);
        return jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregate_id=?", UUID.class, orderId);
    }

    private CheckoutRequest request() { return new CheckoutRequest(List.of(new CheckoutRequest.Line(product, 1))); }

    private UUID createUser(String label) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,first_name,last_name,enabled,account_locked) VALUES(?,?,?,'Test','User',true,false)",
                id, label + "-" + id + "@example.test", "unused-test-hash");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='CUSTOMER'", id);
        return id;
    }
}
