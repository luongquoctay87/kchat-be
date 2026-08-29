package com.kchat.common.dto.chat;

public record RoomDto(
        String id,
        String title,
        String preview,
        String time,
        int unreadCount,
        boolean isOnline,
        boolean isChannel,
        boolean isGroup,
        int memberCount,
        Integer disappearingAfterSeconds,
        String myRole,
        boolean isMuted,
        Long mutedUntilEpochMillis,
        String avatarUrl
) {
}
