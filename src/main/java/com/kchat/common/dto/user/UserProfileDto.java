package com.kchat.common.dto.user;

public record UserProfileDto(
    String id, String username, String email, String displayName, String phone, String avatarUrl) {}
