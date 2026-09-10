package com.kchat.common.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MessageStorageOptionsTest {

  @Test
  void acceptsKnownDisappearingDurations() {
    assertTrue(MessageStorageOptions.isValidDefaultDisappearing(0));
    assertTrue(MessageStorageOptions.isValidDefaultDisappearing(86_400));
    assertTrue(MessageStorageOptions.isValidDefaultDisappearing(604_800));
    assertFalse(MessageStorageOptions.isValidDefaultDisappearing(123));
    assertFalse(MessageStorageOptions.isValidDefaultDisappearing(null));
  }
}
