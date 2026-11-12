package com.cloudresilience.modules.identity.api;

import com.cloudresilience.modules.identity.domain.RoleName;
import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String email, String firstName, String lastName, Set<RoleName> roles) {}
