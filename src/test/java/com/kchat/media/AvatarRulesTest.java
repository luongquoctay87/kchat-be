package com.kchat.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AvatarRulesTest {

  @Test
  void acceptsKnownImageTypes() {
    assertTrue(AvatarRules.isAllowedMime("image/jpeg"));
    assertTrue(AvatarRules.isAllowedMime("image/PNG"));
    assertTrue(AvatarRules.isAllowedMime("image/webp"));
    assertFalse(AvatarRules.isAllowedMime("image/svg+xml"));
    assertFalse(AvatarRules.isAllowedMime("application/pdf"));
    assertFalse(AvatarRules.isAllowedMime(null));
  }
}
