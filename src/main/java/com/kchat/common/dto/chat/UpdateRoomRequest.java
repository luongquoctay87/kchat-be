package com.kchat.common.dto.chat;

import jakarta.validation.constraints.Size;

/**
 * Partial room update. At least one field must be set. {@code disappearingAfterSeconds = 0} clears
 * per-room disappearing (NULL in DB).
 */
public record UpdateRoomRequest(@Size(max = 128) String name, Integer disappearingAfterSeconds) {}
