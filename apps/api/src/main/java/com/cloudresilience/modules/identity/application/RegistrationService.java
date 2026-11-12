package com.cloudresilience.modules.identity.application;

import com.cloudresilience.modules.identity.api.RegisterRequest;
import com.cloudresilience.modules.identity.api.UserResponse;
import com.cloudresilience.modules.identity.domain.RoleName;
import com.cloudresilience.modules.identity.persistence.RoleEntity;
import com.cloudresilience.modules.identity.persistence.RoleRepository;
import com.cloudresilience.modules.identity.persistence.UserEntity;
import com.cloudresilience.modules.identity.persistence.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        String firstName = request.firstName().strip();
        String lastName = request.lastName().strip();
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRegistrationException("Password must not exceed 72 UTF-8 bytes");
        }
        if (firstName.isEmpty() || lastName.isEmpty()) {
            throw new InvalidRegistrationException("First and last name are required");
        }
        if (users.existsByEmail(email)) throw new EmailAlreadyRegisteredException();

        RoleEntity customerRole = roles.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Required CUSTOMER role is missing"));
        UserEntity user = UserEntity.registered(email, passwordEncoder.encode(request.password()), firstName, lastName, customerRole);
        try {
            return toResponse(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException conflict) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    public static UserResponse toResponse(UserEntity user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRoles().stream().map(RoleEntity::getName).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    public static UserResponse toResponse(UserPrincipal principal) {
        return new UserResponse(principal.id(), principal.email(), principal.firstName(), principal.lastName(),
                java.util.Set.copyOf(principal.roles()));
    }
}
