package com.kchat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "kchat.cleanup.retention")
public class MessageRetentionCleanupProperties {

  private boolean enabled = true;

  /** Cron expression (UTC). Default: 02:00 on the 1st of each month. */
  private String cron = "0 0 2 1 * *";

  /** Delete messages (and S3 attachments) older than this many months. */
  private int olderThanMonths = 6;

  /** Max messages hard-deleted per batch. */
  private int batchSize = 500;

  /** Max batches per monthly run (batchSize × maxBatches = upper bound). */
  private int maxBatches = 200;

  /** ShedLock: max lock duration for one monthly run. */
  private String lockAtMostFor = "PT45M";

  /** ShedLock: min lock duration to avoid rapid re-runs. */
  private String lockAtLeastFor = "PT5M";

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

  public int getOlderThanMonths() {
    return olderThanMonths;
  }

  public void setOlderThanMonths(int olderThanMonths) {
    this.olderThanMonths = olderThanMonths;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public int getMaxBatches() {
    return maxBatches;
  }

  public void setMaxBatches(int maxBatches) {
    this.maxBatches = maxBatches;
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
