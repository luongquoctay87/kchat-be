package com.kchat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kchat.password-reset")
public record PasswordResetProperties(int ttlMinutes, int otpLength) {
  public PasswordResetProperties {
    if (ttlMinutes <= 0) {
      ttlMinutes = 30;
    }
    if (otpLength <= 0) {
      otpLength = 6;
    }
  }
}
