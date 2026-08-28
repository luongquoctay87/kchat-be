package com.kchat.common.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebhookSecretsTest {

    @Test
    void matches_acceptsCorrectRejectsWrong() {
        String plain = "dev-ops-alerts-webhook-secret";
        String hash = WebhookSecrets.hash(plain);
        assertTrue(WebhookSecrets.matches(plain, hash));
        assertTrue(WebhookSecrets.matches("  " + plain + "  ", hash));
        assertFalse(WebhookSecrets.matches("wrong", hash));
        assertFalse(WebhookSecrets.matches("", hash));
        assertFalse(WebhookSecrets.matches(plain, ""));
    }
}
