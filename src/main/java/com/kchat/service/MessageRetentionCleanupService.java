package com.kchat.service;

import java.time.Instant;

public interface MessageRetentionCleanupService {

  /**
   * Hard-delete one batch of messages older than {@code cutoff}, including soft-deleted rows, and
   * remove their S3 attachments after commit. Returns count deleted in this batch.
   */
  int purgeMessagesOlderThan(Instant cutoff, int batchSize);
}
