package com.kchat.common.dto.auth;

import com.kchat.common.validation.PasswordRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 512) String registrationToken,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "must be alphanumeric or underscore")
        String username,
        @NotBlank
        @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
        @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
        String password,
        @NotBlank @Size(max = 128) String displayName
) {
}
