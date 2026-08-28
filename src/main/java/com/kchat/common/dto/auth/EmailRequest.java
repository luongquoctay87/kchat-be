package com.kchat.common.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Shared body for endpoints that only need an email address. */
public record EmailRequest(
        @NotBlank @Email @Size(max = 255) String email
) {
}
