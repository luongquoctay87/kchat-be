package com.kchat.common.dto.user;

public record UserSettingsDto(
        boolean pushEnabled,
        String theme,
        String fontSize,
        boolean showOnline,
        boolean enterToSend,
        String privacyDm,
        boolean quietHoursEnabled,
        Integer quietHoursStartHour,
        Integer quietHoursEndHour,
        Integer localCacheRetentionDays,
        Integer defaultDisappearingSeconds
) {
}
