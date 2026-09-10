package com.kchat.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

  @Test
  void sha256IsDeterministic() {
    assertEquals(TokenHasher.sha256Hex("abc"), TokenHasher.sha256Hex("abc"));
    assertNotEquals(TokenHasher.sha256Hex("abc"), TokenHasher.sha256Hex("abd"));
  }

  @Test
  void refreshTokenHasEntropy() {
    String a = TokenHasher.newRefreshToken();
    String b = TokenHasher.newRefreshToken();
    assertNotEquals(a, b);
    assertTrue(a.length() >= 32);
  }
}
