package com.kchat.common.util;

import java.time.LocalTime;

/**
 * Quiet-hours window helpers. Supports overnight ranges (e.g. 22:00–07:00).
 */
public final class QuietHours {

    private QuietHours() {
    }

    public static boolean isActive(LocalTime start, LocalTime end, LocalTime now) {
        if (start == null || end == null || now == null) {
            return false;
        }
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            // Same-day window: 09:00–17:00
            return !now.isBefore(start) && now.isBefore(end);
        }
        // Overnight window: 22:00–07:00
        return !now.isBefore(start) || now.isBefore(end);
    }
}
