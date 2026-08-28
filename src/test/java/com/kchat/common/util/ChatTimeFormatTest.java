package com.kchat.common.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatTimeFormatTest {

    @Test
    void formatsTodayAsClock() {
        Instant now = ZonedDateTime.of(LocalDate.of(2026, 8, 22), LocalTime.of(15, 0), ChatTimeFormat.ZONE)
                .toInstant();
        Instant msg = ZonedDateTime.of(LocalDate.of(2026, 8, 22), LocalTime.of(14, 32), ChatTimeFormat.ZONE)
                .toInstant();
        assertEquals("14:32", ChatTimeFormat.format(msg, now));
    }

    @Test
    void formatsYesterday() {
        Instant now = ZonedDateTime.of(LocalDate.of(2026, 8, 22), LocalTime.of(10, 0), ChatTimeFormat.ZONE)
                .toInstant();
        Instant msg = ZonedDateTime.of(LocalDate.of(2026, 8, 21), LocalTime.of(9, 15), ChatTimeFormat.ZONE)
                .toInstant();
        assertEquals("Hôm qua", ChatTimeFormat.format(msg, now));
    }

    @Test
    void messageClockAlwaysUsesTimeOfDay() {
        Instant msg = ZonedDateTime.of(LocalDate.of(2026, 8, 21), LocalTime.of(9, 15), ChatTimeFormat.ZONE)
                .toInstant();
        assertEquals("09:15", ChatTimeFormat.formatMessageClock(msg));
    }

    @Test
    void formatsOlderAsDayMonth() {
        Instant now = ZonedDateTime.of(LocalDate.of(2026, 8, 22), LocalTime.of(10, 0), ChatTimeFormat.ZONE)
                .toInstant();
        Instant msg = ZonedDateTime.of(LocalDate.of(2026, 8, 10), LocalTime.of(9, 15), ChatTimeFormat.ZONE)
                .toInstant();
        assertEquals("10/08", ChatTimeFormat.format(msg, now));
    }

    @Test
    void formatsFileSize() {
        assertEquals("512 B", ChatTimeFormat.formatFileSize(512L));
        assertEquals("2 KB", ChatTimeFormat.formatFileSize(2048L));
        assertNull(ChatTimeFormat.formatFileSize(null));
    }
}
