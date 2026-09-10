package com.kchat.common.util;

import com.kchat.security.TokenHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Constant-time webhook secret verification (#17). */
public final class WebhookSecrets {

  private WebhookSecrets() {}

  public static String hash(String plainSecret) {
    return TokenHasher.sha256Hex(plainSecret);
  }

  public static boolean matches(String plainSecret, String storedHash) {
    if (plainSecret == null
        || plainSecret.isBlank()
        || storedHash == null
        || storedHash.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        hash(plainSecret.trim()).getBytes(StandardCharsets.UTF_8),
        storedHash.getBytes(StandardCharsets.UTF_8));
  }
}
