package com.cloudresilience.modules.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudresilience.modules.cart.application.CartService;
import com.cloudresilience.modules.identity.application.UserPrincipal;
import com.cloudresilience.modules.identity.domain.RoleName;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"app.messaging.enabled=false", "app.outbox.publisher.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "runIntegrationTests", matches = "true")
class CartIntegrationTest {
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

    @Autowired CartService carts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    private UUID customerId;
    private UUID otherCustomerId;
    private UUID productId;

    @BeforeEach
    void fixtures() {
        jdbc.update("DELETE FROM cart_items");
        jdbc.update("DELETE FROM carts");
        jdbc.update("DELETE FROM inventory_movements");
        jdbc.update("DELETE FROM inventory_reservations");
        jdbc.update("DELETE FROM inventory");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
        customerId = user("buyer@example.test");
        otherCustomerId = user("another@example.test");
        UUID category = UUID.randomUUID();
        productId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories(id,name,slug,active) VALUES(?,?,?,true)", category, "Test items", "test-items");
        jdbc.update("INSERT INTO products(id,sku,name,slug,price,currency,category_id,status) VALUES(?,?,?, ?,?,'USD',?,'ACTIVE')",
                productId, "TEST-1", "Test item", "test-item", new BigDecimal("12.50"), category);
    }

    @Test
    void cartRequiresCustomerRoleAndNeverAllowsAnotherUsersItem() throws Exception {
        mvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/cart").with(authentication(token(customerId, RoleName.ADMIN))))
                .andExpect(status().isForbidden());
        var own = carts.add(customerId, productId, 2);
        UUID item = own.items().getFirst().itemId();
        mvc.perform(patch("/api/v1/cart/items/{id}", item).with(authentication(token(otherCustomerId, RoleName.CUSTOMER)))
                        .with(csrf()).contentType("application/json").content("{\"quantity\":9}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/cart/items/{id}", item).with(authentication(token(otherCustomerId, RoleName.CUSTOMER))).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(carts.get(customerId).items()).hasSize(1);
    }

    @Test
    void addConsolidatesDuplicateProductUsesCurrentPriceAndDoesNotReserveInventory() {
        var first = carts.add(customerId, productId, 2);
        var second = carts.add(customerId, productId, 3);
        assertThat(second.items()).hasSize(1);
        assertThat(second.items().getFirst().quantity()).isEqualTo(5);
        assertThat(second.items().getFirst().lineTotal()).isEqualByComparingTo("62.50");
        assertThat(second.items().getFirst().availableQuantity()).isZero();
        assertThat(second.items().getFirst().productAvailable()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory", Integer.class)).isZero();
        assertThat(carts.summary(customerId).subtotal()).isEqualByComparingTo("62.50");
        assertThat(first.cartId()).isEqualTo(second.cartId());
    }

    @Test
    void rejectsInactiveProductsAndExposesExistingUnavailableLines() {
        var cart = carts.add(customerId, productId, 1);
        jdbc.update("UPDATE products SET status='ARCHIVED' WHERE id=?", productId);
        var displayed = carts.get(customerId);
        assertThat(displayed.items().getFirst().productAvailable()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> carts.add(customerId, productId, 1))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        carts.remove(customerId, cart.items().getFirst().itemId());
        assertThat(carts.get(customerId).items()).isEmpty();
    }

    @Test
    void concurrentFirstCartAndSameProductAddsAreSerializedWithoutDuplicates() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            var start = new CountDownLatch(1);
            var ready = new CountDownLatch(8);
            var created = java.util.stream.IntStream.range(0, 8).mapToObj(i -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start barrier timed out");
                return carts.add(customerId, productId, 1);
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = new java.util.ArrayList<CartService.CartView>();
            for (var task : created) results.add(task.get(15, TimeUnit.SECONDS));
            assertThat(results).extracting(CartService.CartView::cartId).doesNotHaveDuplicates();
            var result = carts.get(customerId);
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().getFirst().quantity()).isEqualTo(8);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM carts WHERE user_id=? AND status='ACTIVE'", Integer.class, customerId)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void concurrentUpdateAndRemoveNeverCreateDuplicateOrPartialRows() throws Exception {
        var item = carts.add(customerId, productId, 1).items().getFirst().itemId();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var update = pool.submit(() -> { start.await(); try { return carts.update(customerId, item, 4).items().size(); } catch (org.springframework.web.server.ResponseStatusException e) { return 0; } });
            var remove = pool.submit(() -> { start.await(); try { return carts.remove(customerId, item).items().size(); } catch (org.springframework.web.server.ResponseStatusException e) { return 0; } });
            start.countDown();
            update.get(10, TimeUnit.SECONDS);
            remove.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id=(SELECT id FROM carts WHERE user_id=?)", Integer.class, customerId)).isZero();
        } finally { pool.shutdownNow(); }
    }

    @Test
    void concurrentAbsoluteQuantityUpdatesSerializeOnTheCartAggregate() throws Exception {
        UUID item = carts.add(customerId, productId, 1).items().getFirst().itemId();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var low = pool.submit(() -> { start.await(); return carts.update(customerId, item, 3); });
            var high = pool.submit(() -> { start.await(); return carts.update(customerId, item, 7); });
            start.countDown();
            low.get(10, TimeUnit.SECONDS);
            high.get(10, TimeUnit.SECONDS);
            var result = carts.get(customerId);
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().getFirst().quantity()).isIn(3, 7);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void transactionFailureRollsBackNewCartAndItemTogether() {
        jdbc.execute("CREATE FUNCTION fail_cart_update() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_cart_update_trigger BEFORE UPDATE ON carts FOR EACH ROW EXECUTE FUNCTION fail_cart_update()");
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> carts.add(customerId, productId, 1)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM carts WHERE user_id=?", Integer.class, customerId)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items", Integer.class)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_cart_update_trigger ON carts");
            jdbc.execute("DROP FUNCTION fail_cart_update()");
        }
    }

    @Test
    void apiValidatesRequestsAndReportsCurrentCartTotals() throws Exception {
        mvc.perform(post("/api/v1/cart/items").with(authentication(token(customerId, RoleName.CUSTOMER))).with(csrf())
                        .contentType("application/json").content("{\"productId\":\"" + productId + "\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        carts.add(customerId, productId, 2);
        mvc.perform(get("/api/v1/cart").with(authentication(token(customerId, RoleName.CUSTOMER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalQuantity").value(2))
                .andExpect(jsonPath("$.items[0].lineTotal").value(25.0));
    }

    private UUID user(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,first_name,last_name,enabled,account_locked) VALUES(?,?,?, ?,?,true,false)",
                id, email, "unused-test-hash", "Test", "Buyer");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='CUSTOMER'", id);
        return id;
    }

    private UserPrincipal principal(UUID id, RoleName role) {
        return new UserPrincipal(id, "buyer@example.test", null, "Test", "Buyer", true, false, List.of(role));
    }
    private UsernamePasswordAuthenticationToken token(UUID id, RoleName role) {
        var principal = principal(id, role);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
