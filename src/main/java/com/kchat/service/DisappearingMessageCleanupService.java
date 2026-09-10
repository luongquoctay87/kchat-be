package com.kchat.service;

public interface DisappearingMessageCleanupService {

  /** Hard-delete expired messages (pinned messages are skipped). Returns count deleted. */
  int purgeExpiredMessages(int batchSize);
}
