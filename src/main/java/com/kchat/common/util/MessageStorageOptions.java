package com.kchat.common.util;

import java.util.Set;

public final class MessageStorageOptions {

    public static final Set<Integer> CACHE_RETENTION_DAYS = Set.of(7, 30, 90);

    /** 0 clears default disappearing (stored as NULL). */
    public static final int CLEAR_DEFAULT_DISAPPEARING = 0;

    public static final Set<Integer> DEFAULT_DISAPPEARING_SECONDS = Set.of(
            86_400,
            604_800,
            2_592_000,
            7_776_000
    );

    private MessageStorageOptions() {
    }

    public static boolean isValidCacheRetention(Integer days) {
        return days != null && CACHE_RETENTION_DAYS.contains(days);
    }

    public static boolean isValidDefaultDisappearing(Integer seconds) {
        if (seconds == null) {
            return false;
        }
        return seconds == CLEAR_DEFAULT_DISAPPEARING || DEFAULT_DISAPPEARING_SECONDS.contains(seconds);
    }
}
