package com.kchat.service.impl;

import com.kchat.media.MediaStorage;
import com.kchat.repository.ChatMessageRepository;
import com.kchat.repository.ExpiredMessageRow;
import com.kchat.repository.MessageAttachmentRepository;
import com.kchat.repository.RoomMemberRepository;
import com.kchat.service.DisappearingMessageCleanupService;
import com.kchat.ws.MessageEventPublisher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DisappearingMessageCleanupServiceImpl implements DisappearingMessageCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DisappearingMessageCleanupServiceImpl.class);

    /** Hard cap so a misconfigured cron cannot lock the DB for too long. */
    static final int MAX_BATCH_SIZE = 1_000;

    private final ChatMessageRepository chatMessageRepository;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MediaStorage mediaStorage;
    private final MessageEventPublisher messageEventPublisher;

    public DisappearingMessageCleanupServiceImpl(
            ChatMessageRepository chatMessageRepository,
            MessageAttachmentRepository messageAttachmentRepository,
            RoomMemberRepository roomMemberRepository,
            MediaStorage mediaStorage,
            MessageEventPublisher messageEventPublisher
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.messageAttachmentRepository = messageAttachmentRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.mediaStorage = mediaStorage;
        this.messageEventPublisher = messageEventPublisher;
    }

    @Override
    @Transactional
    public int purgeExpiredMessages(int batchSize) {
        int limit = Math.min(Math.max(batchSize, 0), MAX_BATCH_SIZE);
        if (limit == 0) {
            return 0;
        }

        List<ExpiredMessageRow> expired = chatMessageRepository.findExpiredForDisappearingCleanup(limit);
        if (expired.isEmpty()) {
            return 0;
        }

        List<UUID> messageIds = expired.stream().map(ExpiredMessageRow::getId).toList();
        // Collect keys first — attachments CASCADE when messages are deleted.
        List<String> mediaKeys = messageAttachmentRepository.findByMessageIdIn(messageIds).stream()
                .map(a -> a.getS3Key())
                .filter(key -> key != null && !key.isBlank())
                .toList();

        int deleted = chatMessageRepository.deleteByIdIn(messageIds);
        if (deleted <= 0) {
            return 0;
        }

        List<ExpiredMessageRow> toNotify = List.copyOf(expired);
        List<String> keysToRemove = List.copyOf(mediaKeys);
        runAfterCommit(() -> {
            keysToRemove.forEach(mediaStorage::deleteQuietly);
            publishDeletions(toNotify);
        });
        log.info("Disappearing cleanup: hard-deleted {} message(s)", deleted);
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

    private void publishDeletions(List<ExpiredMessageRow> expired) {
        Map<UUID, List<UUID>> byRoom = new LinkedHashMap<>();
        for (ExpiredMessageRow row : expired) {
            byRoom.computeIfAbsent(row.getRoomId(), ignored -> new ArrayList<>()).add(row.getId());
        }
        for (Map.Entry<UUID, List<UUID>> entry : byRoom.entrySet()) {
            UUID roomId = entry.getKey();
            List<UUID> memberIds = roomMemberRepository.findActiveMemberUserIds(roomId);
            for (UUID messageId : entry.getValue()) {
                messageEventPublisher.messageDeleted(roomId, messageId.toString(), memberIds);
            }
        }
    }
}
