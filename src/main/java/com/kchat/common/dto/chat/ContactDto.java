package com.kchat.common.dto.chat;

public record ContactDto(
    String id,
    String name,
    String subtitle,
    boolean isOnline,
    String email,
    String avatarUrl,
    boolean isContact,
    String username,
    String phone) {}
