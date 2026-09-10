package com.kchat.common.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RoomMuteOptionsTest {

  @Test
  void unmuteClearsMutedUntil() {
    assertNull(
        RoomMuteOptions.resolveMutedUntil(
            RoomMuteOptions.UNMUTE, Instant.parse("2026-01-01T00:00:00Z")));
  }

  @Test
  void eightHoursAddsDuration() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    Instant until = RoomMuteOptions.resolveMutedUntil(RoomMuteOptions.EIGHT_HOURS, now);
    assertTrue(until.isAfter(now.plusSeconds(RoomMuteOptions.EIGHT_HOURS - 1)));
  }

  @Test
  void isMutedRespectsExpiry() {
    Instant now = Instant.parse("2026-01-01T12:00:00Z");
    assertFalse(RoomMuteOptions.isMuted(now.minusSeconds(60), now));
    assertTrue(RoomMuteOptions.isMuted(now.plusSeconds(60), now));
  }
}
