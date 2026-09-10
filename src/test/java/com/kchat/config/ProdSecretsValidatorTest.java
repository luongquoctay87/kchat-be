package com.kchat.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProdSecretsValidatorTest {

  private JwtProperties jwtProperties;
  private BotProperties botProperties;
  private WebRtcIceProperties webRtcIceProperties;
  private ProdSecretsValidator validator;

  @BeforeEach
  void setUp() {
    jwtProperties = new JwtProperties();
    jwtProperties.setAccessSecret("staging-kchat-access-secret-change-me-32b");
    botProperties = new BotProperties();
    botProperties.setOpsAlertsSecret("staging-ops-alerts-webhook-secret");
    webRtcIceProperties = new WebRtcIceProperties();
    webRtcIceProperties.setAllowDevOpenrelay(false);
    validator = new ProdSecretsValidator(jwtProperties, botProperties, webRtcIceProperties);
  }

  @Test
  void acceptsUniqueSecrets() {
    assertDoesNotThrow(validator::validate);
  }

  @Test
  void rejectsBundledJwtSecret() {
    jwtProperties.setAccessSecret(JwtProperties.DEV_ACCESS_SECRET);
    assertThrows(IllegalStateException.class, validator::validate);
  }

  @Test
  void rejectsBundledWebhookSecret() {
    botProperties.setOpsAlertsSecret(BotProperties.DEV_OPS_ALERTS_SECRET);
    assertThrows(IllegalStateException.class, validator::validate);
  }

  @Test
  void rejectsOpenrelay() {
    webRtcIceProperties.setAllowDevOpenrelay(true);
    assertThrows(IllegalStateException.class, validator::validate);
  }
}
