package com.cloudresilience.modules.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName) {
    public RegisterRequest {
        if (email != null) email = email.strip();
        if (firstName != null) firstName = firstName.strip();
        if (lastName != null) lastName = lastName.strip();
    }
}
