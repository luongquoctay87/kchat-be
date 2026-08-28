package com.kchat.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Fail closed on the prod profile if bundled local secrets leaked into env. */
@Component
@Profile("prod")
public class ProdSecretsValidator {

    private final JwtProperties jwtProperties;
    private final BotProperties botProperties;
    private final WebRtcIceProperties webRtcIceProperties;

    public ProdSecretsValidator(
            JwtProperties jwtProperties,
            BotProperties botProperties,
            WebRtcIceProperties webRtcIceProperties
    ) {
        this.jwtProperties = jwtProperties;
        this.botProperties = botProperties;
        this.webRtcIceProperties = webRtcIceProperties;
    }

    @PostConstruct
    void validate() {
        if (JwtProperties.DEV_ACCESS_SECRET.equals(jwtProperties.getAccessSecret())) {
            throw new IllegalStateException(
                    "JWT_ACCESS_SECRET must not use the bundled dev default");
        }
        if (BotProperties.DEV_OPS_ALERTS_SECRET.equals(botProperties.getOpsAlertsSecret())) {
            throw new IllegalStateException(
                    "KCHAT_OPS_ALERTS_WEBHOOK_SECRET must not use the bundled dev default");
        }
        if (webRtcIceProperties.isAllowDevOpenrelay()) {
            throw new IllegalStateException(
                    "kchat.webrtc.allow-dev-openrelay must be false on the prod profile");
        }
    }
}
