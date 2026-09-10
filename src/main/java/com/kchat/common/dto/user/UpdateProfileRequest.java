package com.kchat.common.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank @Size(max = 128) String displayName,
    @Size(max = 32)
        @Pattern(
            regexp = "^$|^\\+?[0-9][0-9\\s\\-()]{6,30}$",
            message = "must be a valid phone number")
        String phone) {}
