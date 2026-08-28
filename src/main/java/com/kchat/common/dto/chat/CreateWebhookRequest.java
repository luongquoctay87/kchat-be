package com.kchat.common.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWebhookRequest(
        @NotBlank @Size(max = 128) String name
) {
}
