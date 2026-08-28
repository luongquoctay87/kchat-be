package com.kchat.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "kchat.bot")
public class BotProperties {

    /** Bundled local default — rejected at startup on the prod profile. */
    public static final String DEV_OPS_ALERTS_SECRET = "dev-ops-alerts-webhook-secret";

    /**
     * Plain secret used to bootstrap the default webhook on #ops-alerts.
     * Override in production via KCHAT_OPS_ALERTS_WEBHOOK_SECRET.
     */
    @NotBlank
    private String opsAlertsSecret = DEV_OPS_ALERTS_SECRET;

    public String getOpsAlertsSecret() {
        return opsAlertsSecret;
    }

    public void setOpsAlertsSecret(String opsAlertsSecret) {
        this.opsAlertsSecret = opsAlertsSecret;
    }
}
