package com.kchat.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ChatTimeFormat {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private ChatTimeFormat() {
    }

    public static String format(Instant instant) {
        return format(instant, Instant.now());
    }

    /** Clock time for in-chat bubbles — always HH:mm. */
    public static String formatMessageClock(Instant instant) {
        if (instant == null) {
            return "";
        }
        return CLOCK.format(instant.atZone(ZONE));
    }

    /** Relative label for room list / last activity (today → clock, yesterday → Hôm qua). */
    static String format(Instant instant, Instant now) {
        if (instant == null) {
            return "";
        }
        ZonedDateTime zoned = instant.atZone(ZONE);
        LocalDate day = zoned.toLocalDate();
        LocalDate today = now.atZone(ZONE).toLocalDate();
        if (day.equals(today)) {
            return CLOCK.format(zoned);
        }
        if (day.equals(today.minusDays(1))) {
            return "Hôm qua";
        }
        return DAY.format(zoned);
    }

    public static String formatFileSize(Long bytes) {
        if (bytes == null || bytes < 0) {
            return null;
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.0f KB", kb);
        }
        return String.format(Locale.US, "%.1f MB", kb / 1024.0);
    }

    /** Relative last-seen label for contacts directory. */
    public static String formatLastSeen(Instant instant) {
        return formatLastSeen(instant, Instant.now());
    }

    static String formatLastSeen(Instant instant, Instant now) {
        if (instant == null) {
            return "Chưa hoạt động";
        }
        long minutes = java.time.Duration.between(instant, now).toMinutes();
        if (minutes < 1) {
            return "Vừa xong";
        }
        if (minutes < 60) {
            return minutes + " phút trước";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " giờ trước";
        }
        long days = hours / 24;
        if (days == 1) {
            return "Hôm qua";
        }
        if (days < 7) {
            return days + " ngày trước";
        }
        return format(instant, now);
    }
}
