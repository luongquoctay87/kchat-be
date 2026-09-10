package com.kchat.common.dto.user;

public record UpdateUserSettingsRequest(
    Boolean pushEnabled,
    String theme,
    String fontSize,
    Boolean showOnline,
    Boolean enterToSend,
    String privacyDm,
    Boolean quietHoursEnabled,
    Integer quietHoursStartHour,
    Integer quietHoursEndHour,
    Integer localCacheRetentionDays,
    Integer defaultDisappearingSeconds) {}
