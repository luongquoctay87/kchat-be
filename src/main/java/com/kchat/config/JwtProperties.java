package com.kchat.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "kchat.jwt")
public class JwtProperties {

    /** Bundled local default — rejected at startup on the prod profile. */
    public static final String DEV_ACCESS_SECRET = "dev-kchat-access-secret-change-me-32b!!";

    @NotBlank
    @Size(min = 32, message = "access-secret must be at least 32 characters")
    private String accessSecret = DEV_ACCESS_SECRET;

    @Min(1)
    private long accessTtlMinutes = 30;

    @Min(1)
    private long refreshTtlDays = 30;

    public String getAccessSecret() {
        return accessSecret;
    }

    public void setAccessSecret(String accessSecret) {
        this.accessSecret = accessSecret;
    }

    public long getAccessTtlMinutes() {
        return accessTtlMinutes;
    }

    public void setAccessTtlMinutes(long accessTtlMinutes) {
        this.accessTtlMinutes = accessTtlMinutes;
    }

    public long getRefreshTtlDays() {
        return refreshTtlDays;
    }

    public void setRefreshTtlDays(long refreshTtlDays) {
        this.refreshTtlDays = refreshTtlDays;
    }
}
