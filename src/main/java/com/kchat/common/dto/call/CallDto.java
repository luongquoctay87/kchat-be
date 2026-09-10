package com.kchat.common.dto.call;

public record CallDto(
    String id,
    String roomId,
    String initiatorId,
    String initiatorName,
    String calleeId,
    String calleeName,
    String callType,
    String status,
    String createdAt,
    String startedAt,
    String endedAt) {}
