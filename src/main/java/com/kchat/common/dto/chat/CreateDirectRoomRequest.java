package com.kchat.common.dto.chat;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateDirectRoomRequest(
        @NotNull UUID userId
) {
}
