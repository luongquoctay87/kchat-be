package com.kchat.common.dto.chat;

public record WebhookDto(
        String id,
        String name,
        boolean active,
        String createdAt
) {
}
