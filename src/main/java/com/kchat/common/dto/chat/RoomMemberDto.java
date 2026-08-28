package com.kchat.common.dto.chat;

public record RoomMemberDto(
        String id,
        String username,
        String name,
        String role,
        boolean isOnline,
        boolean isMe
) {
}
