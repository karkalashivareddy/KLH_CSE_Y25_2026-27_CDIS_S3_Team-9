package com.cloudresilience.modules.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudresilience.CloudResilienceApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "runIntegrationTests", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SharedSessionIntegrationTest {
    private static final String NAMESPACE = "cloudresilience:phase4-test";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine3.22");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.4.0-alpine3.22").withExposedPorts(6379);
    private static ConfigurableApplicationContext instanceB;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        p.add("spring.datasource.username", POSTGRES::getUsername);
        p.add("spring.datasource.password", POSTGRES::getPassword);
        p.add("spring.session.store-type", () -> "redis");
        p.add("spring.session.redis.namespace", () -> NAMESPACE);
        p.add("spring.data.redis.host", REDIS::getHost);
        p.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort int portA;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper mapper;

    @BeforeAll
    void startIndependentApiInstance() { instanceB = startInstanceB(); }

    @AfterAll
    void stopIndependentApiInstance() { if (instanceB != null) instanceB.close(); }

    @Test
    void authenticatedSessionIsSharedAcrossRestartLogoutAndExpiration() throws Exception {
        String email = "shared-" + UUID.randomUUID() + "@example.test";
        UUID user = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash,first_name,last_name,enabled,account_locked) VALUES(?,?,?,?,?,true,false)",
                user, email, passwords.encode("distributed-session-passphrase"), "Shared", "Session");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='CUSTOMER'", user);

        SessionCookie initial = login(portA, email);
        assertThat(getMe(portA, initial).statusCode()).isEqualTo(200);
        assertThat(getMe(portB(), initial).statusCode()).isEqualTo(200);

        instanceB.close();
        instanceB = startInstanceB();
        assertThat(getMe(portB(), initial).statusCode()).as("Redis-backed session survives API instance restart").isEqualTo(200);

        Csrf csrf = csrf(portB(), initial);
        HttpResponse<String> logout = send(portB(), "POST", "/api/v1/auth/logout", initial.header() + "; " + csrf.cookie(),
                csrf.token(), "");
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(getMe(portA, initial).statusCode()).as("logout on B invalidates session on A").isEqualTo(401);

        SessionCookie expiring = login(portA, email);
        assertThat(getMe(portB(), expiring).statusCode()).isEqualTo(200);
        String key = NAMESPACE + ":sessions:" + expiring.value();
        // Force the persisted server-side TTL to a short interval, then assert the repository rejects it.
        var redisCli = REDIS.execInContainer("redis-cli", "PEXPIRE", key, "250");
        assertThat(redisCli.getExitCode()).isZero();
        Thread.sleep(350); // Wait past the explicitly set Redis TTL without touching the session.
        HttpResponse<String> expired = getMe(portA, expiring);
        assertThat(expired.statusCode()).as("Redis TTL expiration invalidates authentication").isEqualTo(401);
    }

    private ConfigurableApplicationContext startInstanceB() {
        return new SpringApplicationBuilder(CloudResilienceApplication.class)
                .properties("server.port=0", "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(), "spring.flyway.enabled=false",
                        "spring.session.store-type=redis", "spring.session.redis.namespace=" + NAMESPACE,
                        "spring.data.redis.host=" + REDIS.getHost(),
                        "spring.data.redis.port=" + REDIS.getMappedPort(6379))
                .run();
    }

    private int portB() { return instanceB.getEnvironment().getProperty("local.server.port", Integer.class); }

    private SessionCookie login(int port, String email) throws Exception {
        Csrf csrf = csrf(port, null);
        HttpResponse<String> response = send(port, "POST", "/api/v1/auth/login", csrf.cookie(), csrf.token(),
                "{\"email\":\"" + email + "\",\"password\":\"distributed-session-passphrase\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        String header = cookieHeader(response, "SESSION", "JSESSIONID");
        assertThat(header).isNotBlank();
        return new SessionCookie(header, header.substring(header.indexOf('=') + 1), header.substring(0, header.indexOf('=')));
    }

    private Csrf csrf(int port, SessionCookie session) throws Exception {
        HttpResponse<String> response = send(port, "GET", "/api/v1/auth/csrf", session == null ? null : session.header(), null, "");
        assertThat(response.statusCode()).isEqualTo(200);
        var body = mapper.readTree(response.body());
        return new Csrf(body.get("headerName").asText(), body.get("token").asText(), cookieHeader(response, "XSRF-TOKEN"));
    }

    private HttpResponse<String> getMe(int port, SessionCookie session) throws Exception {
        return send(port, "GET", "/api/v1/auth/me", session.header(), null, "");
    }

    private HttpResponse<String> send(int port, String method, String path, String cookie, String csrf, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (cookie != null && !cookie.isBlank()) request.header("Cookie", cookie);
        if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
        if (method.equals("POST")) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        else request.method(method, HttpRequest.BodyPublishers.noBody());
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String cookieHeader(HttpResponse<?> response, String... names) {
        for (String setCookie : response.headers().allValues("Set-Cookie")) {
            for (String name : names) if (setCookie.startsWith(name + "=")) return name + "=" + setCookie.substring(name.length() + 1, setCookie.indexOf(';') < 0 ? setCookie.length() : setCookie.indexOf(';'));
        }
        return "";
    }

    private record SessionCookie(String header, String value, String name) {}
    private record Csrf(String headerName, String token, String cookie) {}
}
