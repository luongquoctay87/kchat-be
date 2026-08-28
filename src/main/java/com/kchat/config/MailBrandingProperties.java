package com.kchat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kchat.mail")
public record MailBrandingProperties(
        String appName,
        String fromName,
        String supportEmail,
        String companyName
) {
    public MailBrandingProperties {
        if (appName == null || appName.isBlank()) {
            appName = "k-chat";
        }
        if (fromName == null || fromName.isBlank()) {
            fromName = appName;
        }
        if (companyName == null || companyName.isBlank()) {
            companyName = appName;
        }
        if (supportEmail == null) {
            supportEmail = "";
        }
    }
}
