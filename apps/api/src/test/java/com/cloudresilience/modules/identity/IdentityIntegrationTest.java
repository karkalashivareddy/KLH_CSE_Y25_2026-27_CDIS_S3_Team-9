package com.cloudresilience.modules.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cloudresilience.modules.identity.persistence.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "runIntegrationTests", matches = "true")
class IdentityIntegrationTest {
    private static final String PASSWORD = "correct-horse-battery-7";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7-alpine3.22");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationRegistrationLoginSessionAndLogoutWorkEndToEnd() throws Exception {
        Csrf csrf = fetchCsrf(null);
        mvc.perform(post("/api/v1/auth/register").cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invalid@example.com","password":"short","firstName":"A","lastName":"B"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("Invalid value"));
        MvcResult registration = mvc.perform(post("/api/v1/auth/register")
                        .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":" Customer@Example.COM ","password":"%s","firstName":" Ada ","lastName":" Lovelace ","roles":["ADMIN"]}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("customer@example.com"))
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.contains("CUSTOMER")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        var stored = users.findWithRolesByEmail("customer@example.com").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(stored.getRoles()).extracting(role -> role.getName().name()).containsExactly("CUSTOMER");

        mvc.perform(post("/api/v1/auth/register").cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"customer@example.com","password":"%s","firstName":"A","lastName":"B"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"customer@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isForbidden()); // CSRF is required even for invalid credentials.

        MockHttpSession anonymousSession = new MockHttpSession();
        Csrf loginCsrf = fetchCsrf(anonymousSession);
        mvc.perform(post("/api/v1/auth/login").session(anonymousSession).cookie(loginCsrf.cookie())
                        .header(loginCsrf.headerName(), loginCsrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"customer@example.com","password":"incorrect-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        MvcResult login = mvc.perform(post("/api/v1/auth/login").session(anonymousSession)
                        .cookie(loginCsrf.cookie()).header(loginCsrf.headerName(), loginCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"CUSTOMER@example.com","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("customer@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.contains("CUSTOMER")));
        mvc.perform(get("/api/v1/admin/access-check").session(session)).andExpect(status().isForbidden());

        Csrf logoutCsrf = fetchCsrf(session);
        mvc.perform(post("/api/v1/auth/logout").session(session).cookie(logoutCsrf.cookie())
                        .header(logoutCsrf.headerName(), logoutCsrf.token()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());

        UUID adminId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id,email,password_hash,first_name,last_name,enabled,account_locked,created_at,updated_at) VALUES (?,?,?,?,?,true,false,?,?)",
                adminId, "admin@example.com", passwordEncoder.encode("test-admin-passphrase"), "Test", "Administrator",
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        jdbc.update("INSERT INTO user_roles (user_id,role_id) SELECT ?, id FROM roles WHERE name='ADMIN'", adminId);
        Csrf adminCsrf = fetchCsrf(null);
        MvcResult adminLogin = mvc.perform(post("/api/v1/auth/login").cookie(adminCsrf.cookie())
                        .header(adminCsrf.headerName(), adminCsrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@example.com","password":"test-admin-passphrase"}
                                """))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/v1/admin/access-check").session((MockHttpSession) adminLogin.getRequest().getSession(false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorization").value("ADMIN access granted"));

        assertThat(registration.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void anonymousRequestsAndInvalidCredentialsAreRejectedWithoutAccountDisclosure() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.trace").doesNotExist());
        mvc.perform(get("/api/v1/admin/access-check")).andExpect(status().isUnauthorized());

        Csrf csrf = fetchCsrf(null);
        mvc.perform(post("/api/v1/auth/login").cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.com","password":"some-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    private Csrf fetchCsrf(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/csrf").session(session == null ? new MockHttpSession() : session))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        String headerName = body.get("headerName").asText();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return new Csrf(headerName, body.get("token").asText(), cookie);
    }

    private record Csrf(String headerName, String token, Cookie cookie) {}
}
