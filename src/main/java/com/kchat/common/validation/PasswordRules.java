package com.kchat.common.validation;

import java.util.regex.Pattern;

public final class PasswordRules {

  public static final int MIN_LENGTH = 8;
  public static final int MAX_LENGTH = 128;

  /** ≥8 chars, at least one lower, upper, digit, and special character. */
  public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,128}$";

  public static final String MESSAGE =
      "Password must be at least 8 characters and include upper, lower, digit, and special character";

  private static final Pattern PATTERN = Pattern.compile(REGEX);

  private PasswordRules() {}

  public static boolean isStrong(String password) {
    return password != null && PATTERN.matcher(password).matches();
  }
}
