package com.kchat.common.util;

import java.util.UUID;

/** UUID ordering aligned with PostgreSQL {@code uuid} comparison (unsigned 128-bit). */
public final class UuidOrder {

  private UuidOrder() {}

  public static int compare(UUID left, UUID right) {
    int msb = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
    if (msb != 0) {
      return msb;
    }
    return Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
  }

  public static UUID smaller(UUID a, UUID b) {
    return compare(a, b) <= 0 ? a : b;
  }

  public static UUID larger(UUID a, UUID b) {
    return compare(a, b) >= 0 ? a : b;
  }
}
