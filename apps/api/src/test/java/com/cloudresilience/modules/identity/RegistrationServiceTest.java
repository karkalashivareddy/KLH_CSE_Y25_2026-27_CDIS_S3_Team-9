package com.cloudresilience.modules.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudresilience.modules.identity.api.RegisterRequest;
import com.cloudresilience.modules.identity.application.EmailAlreadyRegisteredException;
import com.cloudresilience.modules.identity.application.InvalidRegistrationException;
import com.cloudresilience.modules.identity.application.RegistrationService;
import com.cloudresilience.modules.identity.domain.RoleName;
import com.cloudresilience.modules.identity.persistence.RoleEntity;
import com.cloudresilience.modules.identity.persistence.RoleRepository;
import com.cloudresilience.modules.identity.persistence.UserEntity;
import com.cloudresilience.modules.identity.persistence.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder encoder;
    @Mock RoleEntity customerRole;

    private RegistrationService service;
    @BeforeEach
    void setUp() {
        service = new RegistrationService(users, roles, encoder);
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndAssignsOnlyCustomer() {
        when(users.existsByEmail("ada@example.com")).thenReturn(false);
        when(roles.findByName(RoleName.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(customerRole.getName()).thenReturn(RoleName.CUSTOMER);
        when(encoder.encode("correct-horse-battery-7")).thenReturn("$2a$hashed-value");
        when(users.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.register(new RegisterRequest("Ada@Example.COM", "correct-horse-battery-7", "Ada", "Lovelace"));

        assertThat(response.email()).isEqualTo("ada@example.com");
        assertThat(response.roles()).containsExactly(RoleName.CUSTOMER);
        assertThat(response.toString()).doesNotContain("$2a$", "correct-horse-battery-7");
        ArgumentCaptor<UserEntity> user = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).saveAndFlush(user.capture());
        assertThat(user.getValue().getPasswordHash()).isEqualTo("$2a$hashed-value");
        assertThat(user.getValue().getRoles()).extracting(role -> role.getName()).containsExactly(RoleName.CUSTOMER);
    }

    @Test
    void duplicateEmailStopsBeforePasswordHashingOrPersistence() {
        when(users.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("ADA@example.com", "correct-horse-battery-7", "Ada", "Lovelace")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(encoder, never()).encode(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void bcryptInputIsLimitedByUtf8Bytes() {
        String tooLong = "é".repeat(37); // 74 UTF-8 bytes, though only 37 Java characters.
        assertThatThrownBy(() -> service.register(new RegisterRequest("ada@example.com", tooLong, "Ada", "Lovelace")))
                .isInstanceOf(InvalidRegistrationException.class)
                .hasMessage("Password must not exceed 72 UTF-8 bytes");
        verify(encoder, never()).encode(any());
    }
}
