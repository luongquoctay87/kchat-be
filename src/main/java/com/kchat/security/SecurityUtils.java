package com.kchat.security;

import com.kchat.common.exception.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

  private SecurityUtils() {}

  public static Optional<UserPrincipal> currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
      return Optional.empty();
    }
    return Optional.of(principal);
  }

  public static UUID requireUserId() {
    return currentUser()
        .map(UserPrincipal::getId)
        .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
  }
}
