package com.kchat.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kchat.common.exception.ApiException;
import com.kchat.config.MailBrandingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;

class MailServiceImplTest {

  private static final MailBrandingProperties BRANDING =
      new MailBrandingProperties("k-chat", "k-chat", "support@example.com", "k-chat");

  @Test
  void prodWithoutSmtpReturns503WithoutLeakingOtp() {
    Environment environment = mock(Environment.class);
    when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

    MailServiceImpl mail = new MailServiceImpl(null, null, BRANDING, "", false, environment);

    ApiException ex =
        assertThrows(
            ApiException.class, () -> mail.sendRegistrationOtp("a@example.com", "A", "742891", 30));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    assertEquals("mail_unavailable", ex.getCode());
    assertFalse(ex.getMessage().contains("742891"));
  }

  @Test
  void devWithoutSmtpLogsInsteadOfFailing() {
    Environment environment = mock(Environment.class);
    when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

    MailServiceImpl mail = new MailServiceImpl(null, null, BRANDING, "", false, environment);

    assertDoesNotThrow(() -> mail.sendRegistrationOtp("a@example.com", "A", "742891", 30));
  }
}
