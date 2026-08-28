package com.kchat.common.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactMessageRequest(
        @NotBlank @Size(max = 32) String emoji
) {
}
