package com.kchat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kchat.fcm")
public class FcmProperties {

    private boolean enabled = false;

    /** Firebase service account JSON (single line or multiline from env). */
    private String serviceAccountJson = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceAccountJson() {
        return serviceAccountJson;
    }

    public void setServiceAccountJson(String serviceAccountJson) {
        this.serviceAccountJson = serviceAccountJson;
    }

    public boolean isConfigured() {
        return enabled && serviceAccountJson != null && !serviceAccountJson.isBlank();
    }
}
