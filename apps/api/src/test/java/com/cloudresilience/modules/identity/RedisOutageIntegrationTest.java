package com.cloudresilience.modules.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"app.messaging.enabled=false", "app.outbox.publisher.enabled=false"})
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "runIntegrationTests", matches = "true")
class RedisOutageIntegrationTest {
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

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired tools.jackson.databind.ObjectMapper mapper;

    @Test
    void redisOutageFailsReadinessAndDoesNotFallBackToLocalSessions() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        java.util.UUID userId = java.util.UUID.randomUUID();
        String email = "redis-outage-" + userId + "@example.test";
        jdbc.update("INSERT INTO users(id,email,password_hash,first_name,last_name,enabled,account_locked) VALUES(?,?,?,?,?,true,false)",
                userId, email, passwords.encode("redis-outage-password"), "Redis", "Outage");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='CUSTOMER'", userId);
        HttpResponse<String> bootstrap = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/csrf"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(bootstrap.statusCode()).isEqualTo(200);
        String csrfCookie = bootstrap.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow().split(";", 2)[0];
        String csrfToken = mapper.readTree(bootstrap.body()).get("token").asText();
        HttpResponse<String> login = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
                .header("Cookie", csrfCookie).header("X-XSRF-TOKEN", csrfToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"redis-outage-password\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        String sessionCookie = login.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("JSESSIONID=")).findFirst().orElseThrow().split(";", 2)[0];
        REDIS.stop();
        HttpResponse<String> sessionRequest = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/me"))
                .header("Cookie", sessionCookie).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(sessionRequest.statusCode()).isEqualTo(503);
        assertThat(sessionRequest.body()).contains("Shared session storage is temporarily unavailable")
                .doesNotContain("Redis", sessionCookie, "Exception", "stackTrace");
        HttpResponse<String> readiness = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health/readiness"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(readiness.statusCode()).withFailMessage("Readiness response: %s", readiness.body()).isEqualTo(503);
        assertThat(readiness.body()).doesNotContain(sessionCookie, "password", "stackTrace");
    }
}
