package com.cloudresilience.modules.identity.application;

import com.cloudresilience.modules.identity.domain.RoleName;
import com.cloudresilience.modules.identity.persistence.UserEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record UserPrincipal(
        UUID id,
        String email,
        @JsonIgnore String passwordHash,
        String firstName,
        String lastName,
        boolean enabled,
        boolean accountLocked,
        List<RoleName> roles) implements UserDetails, java.io.Serializable {
    private static final long serialVersionUID = 1L;

    public static UserPrincipal from(UserEntity user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getFirstName(),
                user.getLastName(), user.isEnabled(), user.isAccountLocked(),
                user.getRoles().stream().map(role -> role.getName()).sorted().toList());
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !accountLocked; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    @Override public String toString() { return "UserPrincipal[id=" + id + ", roles=" + roles + "]"; }

    public UserPrincipal withoutCredentials() {
        return new UserPrincipal(id, email, null, firstName, lastName, enabled, accountLocked, roles);
    }
}
