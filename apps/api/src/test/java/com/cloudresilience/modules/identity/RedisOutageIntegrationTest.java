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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
        p.add("spring.session.store-type", () -> "redis");
        p.add("spring.data.redis.host", REDIS::getHost);
        p.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort int port;

    @Test
    void redisOutageFailsReadinessAndDoesNotFallBackToLocalSessions() throws Exception {
        REDIS.stop();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> sessionRequest = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/me"))
                .header("Cookie", "JSESSIONID=invalid-session-id").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(sessionRequest.statusCode()).isEqualTo(503);
        assertThat(sessionRequest.body()).contains("Shared session storage is temporarily unavailable")
                .doesNotContain("Redis", "invalid-session-id", "Exception", "stackTrace");
        HttpResponse<String> readiness = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health/readiness"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(readiness.statusCode()).isEqualTo(503);
        assertThat(readiness.body()).doesNotContain("invalid-session-id", "password", "stackTrace");
    }
}
