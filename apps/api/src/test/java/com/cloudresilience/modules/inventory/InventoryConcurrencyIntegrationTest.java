package com.cloudresilience.modules.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudresilience.modules.inventory.application.InventoryService;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "runIntegrationTests", matches = "true")
class InventoryConcurrencyIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine3.22");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    void publicCatalogSearchFiltersDraftAndSupportsBoundedPagination() throws Exception {
        UUID category=UUID.randomUUID(), active=UUID.randomUUID(), draft=UUID.randomUUID();
        jdbc.update("INSERT INTO categories(id,name,slug) VALUES(?,?,?)",category,"Catalog Test","catalog-test-"+category.toString().substring(0,8));
        jdbc.update("INSERT INTO products(id,sku,name,slug,price,category_id,status) VALUES(?,?,?,?,?,?,?)",active,"CAT-"+active.toString().substring(0,8).toUpperCase(),"Published Lamp","published-lamp-"+active.toString().substring(0,8),new java.math.BigDecimal("12.50"),category,"ACTIVE");
        jdbc.update("INSERT INTO products(id,sku,name,slug,price,category_id,status) VALUES(?,?,?,?,?,?,?)",draft,"DRAFT-"+draft.toString().substring(0,8).toUpperCase(),"Hidden Lamp","hidden-lamp-"+draft.toString().substring(0,8),new java.math.BigDecimal("1.00"),category,"DRAFT");
        mvc.perform(get("/api/v1/products").param("q","lamp").param("categoryId",category.toString()).param("page","0").param("size","1").param("sort","price").param("direction","asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].name").value("Published Lamp"));
        mvc.perform(get("/api/v1/products").param("sort","price;drop table products")).andExpect(status().isBadRequest());
    }

    @Test
    void adminProductCreationIsRoleProtected() throws Exception {
        UUID category=UUID.randomUUID();
        jdbc.update("INSERT INTO categories(id,name,slug) VALUES(?,?,?)",category,"Admin Catalog","admin-catalog-"+category.toString().substring(0,8));
        mvc.perform(get("/api/v1/admin/inventory")).andExpect(status().isUnauthorized());
        String json="""
                {"sku":"ADMIN-%s","name":"Admin-created item","slug":"admin-item-%s","price":7.25,"categoryId":"%s"}
                """.formatted(category.toString().substring(0,8).toUpperCase(),category.toString().substring(0,8),category);
        mvc.perform(post("/api/v1/admin/products").with(SecurityMockMvcRequestPostProcessors.csrf()).with(SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/products").with(SecurityMockMvcRequestPostProcessors.csrf()).with(SecurityMockMvcRequestPostProcessors.user("administrator").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void onlyOneSynchronizedAttemptCanReserveTheFinalUnit() throws Exception {
        UUID actor=UUID.randomUUID(), category=UUID.randomUUID(), product=UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,first_name,last_name) VALUES(?,?,?,'Test','Admin')",actor,"inventory-test@localhost","not-used");
        jdbc.update("INSERT INTO categories(id,name,slug) VALUES(?,?,?)",category,"Test","inventory-test");
        jdbc.update("INSERT INTO products(id,sku,name,slug,price,category_id,status) VALUES(?,?,?,?,?,?,'ACTIVE')",product,"TEST-INV","Test product","test-inventory-product",new java.math.BigDecimal("1.00"),category);
        jdbc.update("INSERT INTO inventory(id,product_id,quantity_on_hand,quantity_reserved) VALUES(?,?,1,0)",UUID.randomUUID(),product);

        var ready=new CountDownLatch(2); var start=new CountDownLatch(1); var successes=new AtomicInteger(); var conflicts=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var attempts=java.util.stream.IntStream.range(0,2).mapToObj(i->pool.submit(()->{
                ready.countDown();
                try {
                    if(!start.await(5,TimeUnit.SECONDS)) throw new AssertionError("barrier timed out");
                    inventory.reserve(product,1,"concurrency integration test",actor); successes.incrementAndGet();
                } catch(ResponseStatusException expected) { if(expected.getStatusCode().value()==409) conflicts.incrementAndGet(); else throw expected; }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            })).toList();
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            for(var attempt:attempts) attempt.get(10,TimeUnit.SECONDS);
        }
        assertThat(successes).hasValue(1); assertThat(conflicts).hasValue(1);
        assertThat(inventory.inventory(product)).containsEntry("quantity_on_hand",1L).containsEntry("quantity_reserved",1L).containsEntry("available_quantity",0L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservations WHERE product_id=? AND status='RESERVED'",Integer.class,product)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE product_id=? AND movement_type='RESERVATION'",Integer.class,product)).isEqualTo(1);
    }

    @Test
    void adjustmentReservationReleaseAndFailedWritesKeepLedgerAtomic() {
        UUID actor=UUID.randomUUID(), category=UUID.randomUUID(), product=UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,first_name,last_name) VALUES(?,?,?,'Test','Admin')",actor,"inventory-lifecycle-"+actor+"@localhost","not-used");
        jdbc.update("INSERT INTO categories(id,name,slug) VALUES(?,?,?)",category,"Lifecycle","lifecycle-"+category.toString().substring(0,8));
        jdbc.update("INSERT INTO products(id,sku,name,slug,price,category_id) VALUES(?,?,?,?,?,?)",product,"LIFE-"+product.toString().substring(0,8).toUpperCase(),"Lifecycle item","lifecycle-item-"+product.toString().substring(0,8),new java.math.BigDecimal("2.00"),category);
        jdbc.update("INSERT INTO inventory(id,product_id,quantity_on_hand) VALUES(?,?,4)",UUID.randomUUID(),product);

        inventory.adjust(product,3,"Received shipment",actor);
        var created=inventory.reserve(product,2,"Hold units",actor);
        UUID reservation=(UUID)created.get("reservationId");
        inventory.release(product,reservation,"Hold cancelled",actor);
        assertThat(inventory.inventory(product)).containsEntry("quantity_on_hand",7L).containsEntry("quantity_reserved",0L).containsEntry("available_quantity",7L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE product_id=?",Integer.class,product)).isEqualTo(3);
        assertThatThrownBy(()->inventory.reserve(product,8,"Too many",actor)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->inventory.release(product,reservation,"Double release",actor)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->inventory.adjust(product,1,"Invalid actor rollback",UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(inventory.inventory(product)).containsEntry("quantity_on_hand",7L).containsEntry("quantity_reserved",0L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movements WHERE product_id=?",Integer.class,product)).isEqualTo(3);
    }
}
