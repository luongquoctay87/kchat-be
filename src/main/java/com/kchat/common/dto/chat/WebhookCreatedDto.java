package com.kchat.common.dto.chat;

public record WebhookCreatedDto(
    String id,
    String name,
    /** Plain secret — returned only once on create. */
    String secret,
    String createdAt) {}
