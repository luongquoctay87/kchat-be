package com.kchat.service.impl;

import com.kchat.media.MediaStorage;
import com.kchat.repository.ChatMessageRepository;
import com.kchat.repository.ExpiredMessageRow;
import com.kchat.repository.MessageAttachmentRepository;
import com.kchat.service.MessageRetentionCleanupService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MessageRetentionCleanupServiceImpl implements MessageRetentionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(MessageRetentionCleanupServiceImpl.class);

    /** Hard cap so a misconfigured job cannot lock the DB for too long per batch. */
    static final int MAX_BATCH_SIZE = 1_000;

    private final ChatMessageRepository chatMessageRepository;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final MediaStorage mediaStorage;

    public MessageRetentionCleanupServiceImpl(
            ChatMessageRepository chatMessageRepository,
            MessageAttachmentRepository messageAttachmentRepository,
            MediaStorage mediaStorage
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.messageAttachmentRepository = messageAttachmentRepository;
        this.mediaStorage = mediaStorage;
    }

    @Override
    @Transactional
    public int purgeMessagesOlderThan(Instant cutoff, int batchSize) {
        if (cutoff == null) {
            return 0;
        }
        int limit = Math.min(Math.max(batchSize, 0), MAX_BATCH_SIZE);
        if (limit == 0) {
            return 0;
        }

        List<ExpiredMessageRow> old = chatMessageRepository.findOlderThanForRetentionCleanup(cutoff, limit);
        if (old.isEmpty()) {
            return 0;
        }

        List<UUID> messageIds = old.stream().map(ExpiredMessageRow::getId).toList();
        // Collect keys first — attachments CASCADE when messages are deleted.
        List<String> mediaKeys = messageAttachmentRepository.findByMessageIdIn(messageIds).stream()
                .map(a -> a.getS3Key())
                .filter(key -> key != null && !key.isBlank())
                .toList();

        int deleted = chatMessageRepository.deleteByIdIn(messageIds);
        if (deleted <= 0) {
            return 0;
        }

        List<String> keysToRemove = List.copyOf(mediaKeys);
        runAfterCommit(() -> keysToRemove.forEach(mediaStorage::deleteQuietly));
        log.info(
                "Retention cleanup: hard-deleted {} message(s) older than {} ({} S3 object(s) queued)",
                deleted,
                cutoff,
                keysToRemove.size()
        );
        return deleted;
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
