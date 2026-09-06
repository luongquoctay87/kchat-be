package com.kchat.common.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoomMemberDto(
        String id,
        String username,
        String name,
        String role,
        @JsonProperty("is_online") boolean isOnline,
        @JsonProperty("is_me") boolean isMe
) {
}
