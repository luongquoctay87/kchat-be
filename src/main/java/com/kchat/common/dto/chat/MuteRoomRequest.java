package com.kchat.common.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Mute notifications for the current user's membership in a room.
 * {@code duration_seconds}: 0 = unmute, 28800 = 8h, 604800 = 1 week, -1 = forever.
 */
public record MuteRoomRequest(
        @NotNull @JsonProperty("duration_seconds") Integer durationSeconds
) {
}
