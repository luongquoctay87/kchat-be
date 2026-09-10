package com.kchat.common.util;

import java.time.Instant;

public final class RoomMuteOptions {

  /** Clear mute. */
  public static final int UNMUTE = 0;

  public static final int EIGHT_HOURS = 28_800;
  public static final int ONE_WEEK = 604_800;

  /** Mute until far-future sentinel. */
  public static final int FOREVER = -1;

  private static final Instant FOREVER_UNTIL = Instant.parse("9999-12-31T23:59:59Z");

  private RoomMuteOptions() {}

  public static boolean isValidDuration(int durationSeconds) {
    return durationSeconds == UNMUTE
        || durationSeconds == EIGHT_HOURS
        || durationSeconds == ONE_WEEK
        || durationSeconds == FOREVER;
  }

  public static Instant resolveMutedUntil(int durationSeconds, Instant now) {
    if (durationSeconds == UNMUTE) {
      return null;
    }
    if (durationSeconds == FOREVER) {
      return FOREVER_UNTIL;
    }
    return now.plusSeconds(durationSeconds);
  }

  public static boolean isMuted(Instant mutedUntil, Instant now) {
    return mutedUntil != null && mutedUntil.isAfter(now);
  }
}
