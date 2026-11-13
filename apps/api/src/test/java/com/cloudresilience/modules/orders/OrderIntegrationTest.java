package com.cloudresilience.modules.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudresilience.modules.identity.application.UserPrincipal;
import com.cloudresilience.modules.identity.domain.RoleName;
import com.cloudresilience.modules.orders.api.CheckoutRequest;
import com.cloudresilience.modules.orders.application.CheckoutService;
import com.cloudresilience.modules.orders.application.CheckoutTransactionService;
import com.cloudresilience.modules.orders.application.OrderQueryService;
import com.cloudresilience.modules.orders.application.OrderWorkflowService;
import com.cloudresilience.modules.orders.domain.OrderStatus;
import com.cloudresilience.modules.orders.domain.PaymentStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"app.messaging.enabled=false", "app.outbox.publisher.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "runIntegrationTests", matches = "true")
class OrderIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine3.22");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.4.0-alpine3.22").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        p.add("spring.datasource.username", POSTGRES::getUsername);
        p.add("spring.datasource.password", POSTGRES::getPassword);
        p.add("spring.session.store-type", () -> "redis");
        p.add("spring.data.redis.host", REDIS::getHost);
        p.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CheckoutService checkout;
    @Autowired CheckoutTransactionService checkoutTransactions;
    @Autowired OrderQueryService queries;
    @Autowired OrderWorkflowService workflow;
    @Autowired MockMvc mvc;

    private UUID customer;
    private UUID otherCustomer;
    private UUID admin;
    private UUID product;
    private UUID category;
    private UUID cartId;

    @BeforeEach
    void fixture() {
        customer = createUser("buyer");
        otherCustomer = createUser("other");
        admin = createUser("operator", "ADMIN");
        category = UUID.randomUUID();
        product = UUID.randomUUID();
        cartId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO categories(id,name,slug,active) VALUES(?,?,?,true)", category, "Order test", "order-test-" + suffix);
        jdbc.update("INSERT INTO products(id,sku,name,slug,price,currency,category_id,status) VALUES(?,?,?,?,?,'USD',?,'ACTIVE')",
                product, "ORD-" + suffix.toUpperCase(), "Snapshot lamp", "snapshot-lamp-" + suffix, new BigDecimal("12.35"), category);
        jdbc.update("INSERT INTO inventory(id,product_id,quantity_on_hand,quantity_reserved) VALUES(?,?,10,0)", UUID.randomUUID(), product);
        jdbc.update("INSERT INTO carts(id,user_id,status) VALUES(?,?,'ACTIVE')", cartId, customer);
        jdbc.update("INSERT INTO cart_items(id,cart_id,product_id,quantity) VALUES(?,?,?,?)", UUID.randomUUID(), cartId, product, 2);
    }

    @Test
    void checkoutCreatesSnapshotsReservesAtomicallyAndClearsOnlyPurchasedCartLines() {
        var order = checkout.checkout(customer, "order-basic-0001", request(product, 2));
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(order.orderNumber()).startsWith("CR-").hasSize(27);
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().getFirst().sku()).startsWith("ORD-");
        assertThat(order.items().getFirst().productName()).isEqualTo("Snapshot lamp");
        assertThat(order.items().getFirst().unitPrice()).isEqualByComparingTo("12.35");
        assertThat(order.items().getFirst().lineTotal()).isEqualByComparingTo("24.70");
        assertThat(order.totalAmount()).isEqualByComparingTo("24.70");
        assertThat(jdbc.queryForObject("SELECT quantity_reserved FROM inventory WHERE product_id=?", Long.class, product)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservations WHERE order_id=? AND status='RESERVED'", Integer.class, order.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id=?", Integer.class, cartId)).isZero();
        jdbc.update("UPDATE products SET name='Renamed lamp',price=99.99,status='ARCHIVED' WHERE id=?", product);
        var historical = queries.customerOrder(customer, order.id());
        assertThat(historical.items().getFirst().productName()).isEqualTo("Snapshot lamp");
        assertThat(historical.items().getFirst().unitPrice()).isEqualByComparingTo("12.35");
    }

    @Test
    void insufficientSecondProductRollsBackFirstReservationOrderAndIdempotencyClaim() {
        UUID second = createProduct("1.00", 1);
        jdbc.update("INSERT INTO cart_items(id,cart_id,product_id,quantity) VALUES(?,?,?,?)", UUID.randomUUID(), cartId, second, 2);
        var failure = catchThrowableOfType(() -> checkout.checkout(customer, "order-atomic-0001",
                new CheckoutRequest(List.of(new CheckoutRequest.Line(product, 2), new CheckoutRequest.Line(second, 2)))),
                ResponseStatusException.class);
        assertThat(failure.getStatusCode().value()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT quantity_reserved FROM inventory WHERE product_id=?", Long.class, product)).isZero();
        assertThat(jdbc.queryForObject("SELECT quantity_reserved FROM inventory WHERE product_id=?", Long.class, second)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_orders WHERE customer_id=?", Integer.class, customer)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM checkout_idempotency WHERE customer_id=?", Integer.class, customer)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id NOT IN (SELECT id FROM customer_orders)",
                Integer.class)).isZero();
    }

    @Test
    void orderLifecycleEmitsMinimalOutboxEventsInTheSameCommit() {
        var order = checkout.checkout(customer, "order-events-0001", request(product, 2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='OrderPlaced'",
                Integer.class, order.id())).isEqualTo(1);
        String payload = jdbc.queryForObject("SELECT payload::text FROM outbox_events WHERE aggregate_id=? AND event_type='OrderPlaced'",
                String.class, order.id());
        assertThat(payload).contains(order.orderNumber()).contains("CONFIRMED")
                .doesNotContain("@example.test").doesNotContain("password").doesNotContain("JSESSIONID")
                .doesNotContain("csrf");

        workflow.cancelCustomerOrder(customer, order.id(), "Customer changed mind");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='OrderCancelled'",
                Integer.class, order.id())).isEqualTo(1);

        UUID lifecycleCustomer = createUser("lifecycle");
        createCart(lifecycleCustomer, product, 1);
        var lifecycle = checkout.checkout(lifecycleCustomer, "order-lifecycle-0001", request(product, 1));
        workflow.transitionAdmin(admin, lifecycle.id(), OrderStatus.PROCESSING, "packed");
        workflow.transitionAdmin(admin, lifecycle.id(), OrderStatus.SHIPPED, "handed to carrier");
        workflow.transitionAdmin(admin, lifecycle.id(), OrderStatus.DELIVERED, "delivered");
        assertThat(jdbc.query("SELECT event_type FROM outbox_events WHERE aggregate_id=? ORDER BY created_at,event_type",
                (rs, row) -> rs.getString(1), lifecycle.id()))
                .containsExactly("OrderPlaced", "OrderProcessingStarted", "OrderShipped", "OrderDelivered");
    }

    @Test
    void failedPaymentPersistsFailureAndReleasesEveryReservationOnce() {
        var prepared = checkoutTransactions.prepare(customer, "order-decline-0001", request(product, 2));
        checkoutTransactions.completePayment(prepared.paymentId(), new com.cloudresilience.modules.orders.application.PaymentGateway.PaymentResult(
                PaymentStatus.DECLINED, "SIM-DECLINE-1", "SIMULATED_DECLINE"));
        var failed = queries.customerOrder(customer, prepared.orderId());
        assertThat(failed.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(failed.paymentStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(jdbc.queryForObject("SELECT quantity_reserved FROM inventory WHERE product_id=?", Long.class, product)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_reservations WHERE order_id=?", String.class, prepared.orderId())).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE reference_id=(SELECT id FROM inventory_reservations WHERE order_id=?) AND movement_type='ORDER_RESERVATION_RELEASE'",
                Integer.class, prepared.orderId())).isEqualTo(1);
        var replay = checkout.checkout(customer, "order-decline-0001", request(product, 2));
        assertThat(replay.id()).isEqualTo(prepared.orderId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id=?", Integer.class, prepared.orderId())).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyReturnsOriginalOrderAndRejectsDifferentPayload() {
        var first = checkout.checkout(customer, "order-idempotent-0001", request(product, 2));
        var replay = checkout.checkout(customer, "order-idempotent-0001", request(product, 2));
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_orders WHERE customer_id=?", Integer.class, customer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservations WHERE order_id=?", Integer.class, first.id())).isEqualTo(1);
        var changed = catchThrowableOfType(() -> checkout.checkout(customer, "order-idempotent-0001", request(product, 3)),
                ResponseStatusException.class);
        assertThat(changed.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void concurrentDuplicateCheckoutCreatesOneOrderReservationAndLogicalPayment() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var attempts = java.util.stream.IntStream.range(0, 2).mapToObj(index -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("barrier timed out");
                return checkout.checkout(customer, "order-race-key-0001", request(product, 2));
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var first = attempts.get(0).get(15, TimeUnit.SECONDS);
            var second = attempts.get(1).get(15, TimeUnit.SECONDS);
            assertThat(first.id()).isEqualTo(second.id());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_orders WHERE customer_id=?", Integer.class, customer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id=(SELECT id FROM customer_orders WHERE customer_id=?)", Integer.class, customer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservations WHERE order_id=(SELECT id FROM customer_orders WHERE customer_id=?)", Integer.class, customer)).isEqualTo(1);
    }

    @Test
    void competingCustomersCannotBuyTheSameFinalUnit() throws Exception {
        UUID secondCart = UUID.randomUUID();
        jdbc.update("UPDATE inventory SET quantity_on_hand=1 WHERE product_id=?", product);
        jdbc.update("INSERT INTO carts(id,user_id,status) VALUES(?,?,'ACTIVE')", secondCart, otherCustomer);
        jdbc.update("INSERT INTO cart_items(id,cart_id,product_id,quantity) VALUES(?,?,?,1)", UUID.randomUUID(), secondCart, product);
        jdbc.update("UPDATE cart_items SET quantity=1 WHERE cart_id=?", cartId);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var conflicts = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var attempts = List.of(
                    pool.submit(() -> { attemptCheckout(customer, "final-unit-customer-a-1", ready, start, successes, conflicts); return null; }),
                    pool.submit(() -> { attemptCheckout(otherCustomer, "final-unit-customer-b-1", ready, start, successes, conflicts); return null; }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var attempt : attempts) attempt.get(15, TimeUnit.SECONDS);
        }
        assertThat(successes).hasValue(1);
        assertThat(conflicts).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT quantity_on_hand FROM inventory WHERE product_id=?", Long.class, product)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT quantity_reserved FROM inventory WHERE product_id=?", Long.class, product)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservations WHERE product_id=? AND status='RESERVED'", Integer.class, product)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE product_id=? AND movement_type='ORDER_RESERVATION'", Integer.class, product)).isEqualTo(1);
    }

    @Test
    void cancellationReleasesOnceAndAdminProcessingConsumesOnce() {
        var cancelled = checkout.checkout(customer, "order-cancel-0001", request(product, 2));
        workflow.cancelCustomerOrder(customer, cancelled.id(), "Customer changed mind");
        assertThat(queries.customerOrder(customer, cancelled.id()).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(queries.customerPayment(customer, cancelled.id()).status()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(jdbc.queryForObject("SELECT quantity_reserved FROM inventory WHERE product_id=?", Long.class, product)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_reservations WHERE order_id=?", String.class, cancelled.id())).isEqualTo("RELEASED");
        assertThat(catchThrowableOfType(() -> workflow.cancelCustomerOrder(customer, cancelled.id(), "again"), ResponseStatusException.class)
                .getStatusCode().value()).isEqualTo(409);

        // A second fixture/order demonstrates the consume transition independently.
        UUID processingCustomer = createUser("processing");
        UUID processingCart = createCart(processingCustomer, product, 3);
        var processing = checkout.checkout(processingCustomer, "order-process-0001", request(product, 3));
        workflow.transitionAdmin(admin, processing.id(), OrderStatus.PROCESSING, "Packed for fulfillment");
        assertThat(queries.customerOrder(processingCustomer, processing.id()).status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(jdbc.queryForObject("SELECT quantity_on_hand FROM inventory WHERE product_id=?", Long.class, product)).isEqualTo(7L);
        assertThat(jdbc.queryForObject("SELECT quantity_reserved FROM inventory WHERE product_id=?", Long.class, product)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM inventory_reservations WHERE order_id=?", String.class, processing.id())).isEqualTo("COMMITTED");
        assertThat(catchThrowableOfType(() -> workflow.transitionAdmin(admin, processing.id(), OrderStatus.PROCESSING, "repeat"), ResponseStatusException.class)
                .getStatusCode().value()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE reference_id=(SELECT id FROM inventory_reservations WHERE order_id=?) AND movement_type='ORDER_COMMIT'",
                Integer.class, processing.id())).isEqualTo(1);
        assertThat(processingCart).isNotNull();
    }

    @Test
    void apiEnforcesOwnershipRoleBoundariesAndCsrf() throws Exception {
        var ownOrder = checkout.checkout(customer, "order-access-0001", request(product, 2));
        mvc.perform(get("/api/v1/orders/{id}", ownOrder.id()).with(authentication(token(customer, RoleName.CUSTOMER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderNumber").value(ownOrder.orderNumber()));
        mvc.perform(get("/api/v1/orders/{id}", ownOrder.id()).with(authentication(token(otherCustomer, RoleName.CUSTOMER))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/orders").with(authentication(token(customer, RoleName.CUSTOMER))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders").with(authentication(token(admin, RoleName.ADMIN))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders/{id}/cancel", ownOrder.id())
                        .with(authentication(token(customer, RoleName.CUSTOMER))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"cancel\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders/{id}/cancel", ownOrder.id()).with(authentication(token(customer, RoleName.CUSTOMER))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"customer request\"}"))
                .andExpect(status().isNoContent());
    }

    private void attemptCheckout(UUID buyer, String key, CountDownLatch ready, CountDownLatch start,
            AtomicInteger successes, AtomicInteger conflicts) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("barrier timed out");
        try {
            checkout.checkout(buyer, key, request(product, 1));
            successes.incrementAndGet();
        } catch (ResponseStatusException failure) {
            if (failure.getStatusCode().value() == 409) conflicts.incrementAndGet();
            else throw failure;
        }
    }

    private UUID createProduct(String price, long stock) {
        UUID id = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO products(id,sku,name,slug,price,currency,category_id,status) VALUES(?,?,?,?,?,'USD',?,'ACTIVE')",
                id, "ORD-" + suffix.toUpperCase(), "Additional item", "additional-item-" + suffix, new BigDecimal(price), category);
        jdbc.update("INSERT INTO inventory(id,product_id,quantity_on_hand) VALUES(?,?,?)", UUID.randomUUID(), id, stock);
        return id;
    }

    private UUID createCart(UUID user, UUID item, int quantity) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO carts(id,user_id,status) VALUES(?,?,'ACTIVE')", id, user);
        jdbc.update("INSERT INTO cart_items(id,cart_id,product_id,quantity) VALUES(?,?,?,?)", UUID.randomUUID(), id, item, quantity);
        return id;
    }

    private UUID createUser(String label) { return createUser(label, "CUSTOMER"); }
    private UUID createUser(String label, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,first_name,last_name,enabled,account_locked) VALUES(?,?,?,'Test','User',true,false)",
                id, label + "-" + id + "@example.test", "unused-test-hash");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name=?", id, role);
        return id;
    }

    private CheckoutRequest request(UUID productId, int quantity) {
        return new CheckoutRequest(List.of(new CheckoutRequest.Line(productId, quantity)));
    }

    private UsernamePasswordAuthenticationToken token(UUID id, RoleName role) {
        UserPrincipal principal = new UserPrincipal(id, "fixture@example.test", null, "Test", "User", true, false, List.of(role));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
