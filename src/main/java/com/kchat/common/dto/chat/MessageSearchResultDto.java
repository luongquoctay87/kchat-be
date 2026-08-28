package com.kchat.common.dto.chat;

public record MessageSearchResultDto(
        String id,
        String author,
        String time,
        String snippet,
        Long createdAt
) {
}
