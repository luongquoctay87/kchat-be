package com.kchat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "kchat.cleanup.disappearing")
public class DisappearingMessageCleanupProperties {

    private boolean enabled = true;

    /** Cron expression (UTC). Default: every 5 minutes. */
    private String cron = "0 */5 * * * *";

    /** Max messages hard-deleted per job run. */
    private int batchSize = 500;

    /** ShedLock: max lock duration for one run. */
    private String lockAtMostFor = "PT4M";

    /** ShedLock: min lock duration to avoid rapid re-runs. */
    private String lockAtLeastFor = "PT1M";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getLockAtMostFor() {
        return lockAtMostFor;
    }

    public void setLockAtMostFor(String lockAtMostFor) {
        this.lockAtMostFor = lockAtMostFor;
    }

    public String getLockAtLeastFor() {
        return lockAtLeastFor;
    }

    public void setLockAtLeastFor(String lockAtLeastFor) {
        this.lockAtLeastFor = lockAtLeastFor;
    }
}
