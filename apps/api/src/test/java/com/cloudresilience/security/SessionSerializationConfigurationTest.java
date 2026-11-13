package com.cloudresilience.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudresilience.modules.identity.application.UserPrincipal;
import com.cloudresilience.modules.identity.domain.RoleName;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionSerializationConfigurationTest {
    private final RedisSerializer<Object> serializer = new SessionSerializationConfiguration()
            .springSessionDefaultRedisSerializer();

    @Test
    void securityContextRoundTripsWithSanitizedPrincipalAndAuthorities() {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "customer@example.test", null,
                "Test", "Customer", true, false, List.of(RoleName.CUSTOMER));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        byte[] bytes = serializer.serialize(context);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(org.springframework.security.core.context.SecurityContext.class);
        var restoredAuthentication = ((org.springframework.security.core.context.SecurityContext) restored).getAuthentication();
        assertThat(restoredAuthentication.getPrincipal()).isEqualTo(principal);
        assertThat(restoredAuthentication.getCredentials()).isNull();
        assertThat(restoredAuthentication.getAuthorities()).extracting("authority").containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void rejectsUnapprovedRuntimeTypeIds() {
        byte[] untrusted = "{\"@class\":\"java.lang.ProcessBuilder\",\"value\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> serializer.deserialize(untrusted)).isInstanceOf(RuntimeException.class);
    }
}
