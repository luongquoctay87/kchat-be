package com.kchat.common.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BotAlertRequest(
        @NotBlank @Size(max = 256) String title,
        @Size(max = 256) String service,
        @Size(max = 4000) String text
) {
}
