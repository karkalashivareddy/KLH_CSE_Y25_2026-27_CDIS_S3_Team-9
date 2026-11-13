package com.cloudresilience.modules.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudresilience.modules.identity.application.UserPrincipal;
import com.cloudresilience.modules.identity.domain.RoleName;
import java.util.List;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.Test;

class UserPrincipalTest {
    @Test
    void mapsEnabledAndLockedStateAndAuthoritiesFromServerSideRoles() {
        UserPrincipal customer = new UserPrincipal(UUID.randomUUID(), "customer@example.com", "encoded", "Ada", "Lovelace",
                true, false, List.of(RoleName.CUSTOMER));
        UserPrincipal locked = new UserPrincipal(UUID.randomUUID(), "locked@example.com", "encoded", "A", "B",
                true, true, List.of(RoleName.ADMIN));
        UserPrincipal disabled = new UserPrincipal(UUID.randomUUID(), "disabled@example.com", "encoded", "A", "B",
                false, false, List.of(RoleName.CUSTOMER));

        assertThat(customer.getAuthorities()).extracting("authority").containsExactly("ROLE_CUSTOMER");
        assertThat(customer.isEnabled()).isTrue();
        assertThat(customer.isAccountNonLocked()).isTrue();
        assertThat(locked.isAccountNonLocked()).isFalse();
        assertThat(disabled.isEnabled()).isFalse();
        assertThat(customer.toString()).doesNotContain("encoded");
    }

    @Test
    void sessionPrincipalIsSerializableWithoutRetainingPasswordHash() throws Exception {
        UserPrincipal authenticated = new UserPrincipal(UUID.randomUUID(), "customer@example.com", "encoded-secret-hash",
                "Ada", "Lovelace", true, false, List.of(RoleName.CUSTOMER)).withoutCredentials();
        var token = UsernamePasswordAuthenticationToken.authenticated(authenticated, null, authenticated.getAuthorities());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(token);
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) { output.writeObject(context); }
        assertThat(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("encoded-secret-hash");
    }
}
