package com.kchat.common.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class QuietHoursTest {

    @Test
    void sameDayWindow() {
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(17, 0);
        assertTrue(QuietHours.isActive(start, end, LocalTime.of(9, 0)));
        assertTrue(QuietHours.isActive(start, end, LocalTime.of(12, 0)));
        assertFalse(QuietHours.isActive(start, end, LocalTime.of(17, 0)));
        assertFalse(QuietHours.isActive(start, end, LocalTime.of(8, 0)));
    }

    @Test
    void overnightWindow() {
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(7, 0);
        assertTrue(QuietHours.isActive(start, end, LocalTime.of(22, 0)));
        assertTrue(QuietHours.isActive(start, end, LocalTime.of(23, 30)));
        assertTrue(QuietHours.isActive(start, end, LocalTime.of(3, 0)));
        assertFalse(QuietHours.isActive(start, end, LocalTime.of(7, 0)));
        assertFalse(QuietHours.isActive(start, end, LocalTime.of(12, 0)));
    }

    @Test
    void disabledWhenNullOrEqual() {
        assertFalse(QuietHours.isActive(null, LocalTime.of(7, 0), LocalTime.of(23, 0)));
        assertFalse(QuietHours.isActive(LocalTime.of(22, 0), LocalTime.of(22, 0), LocalTime.of(22, 0)));
    }
}
