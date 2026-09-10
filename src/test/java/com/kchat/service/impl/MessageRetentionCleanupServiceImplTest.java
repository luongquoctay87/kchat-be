package com.kchat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kchat.entity.MessageAttachment;
import com.kchat.media.MediaStorage;
import com.kchat.repository.ChatMessageRepository;
import com.kchat.repository.ExpiredMessageRow;
import com.kchat.repository.MessageAttachmentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageRetentionCleanupServiceImplTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private MessageAttachmentRepository messageAttachmentRepository;
    @Mock
    private MediaStorage mediaStorage;

    private MessageRetentionCleanupServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MessageRetentionCleanupServiceImpl(
                chatMessageRepository,
                messageAttachmentRepository,
                mediaStorage
        );
    }

    @Test
    void purgeMessagesOlderThan_deletesDbThenMedia() {
        Instant cutoff = Instant.parse("2025-01-01T00:00:00Z");
        UUID roomId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        ExpiredMessageRow old = row(messageId, roomId);
        MessageAttachment attachment = new MessageAttachment();
        attachment.setS3Key("kchat/rooms/" + roomId + "/file.jpg");

        when(chatMessageRepository.findOlderThanForRetentionCleanup(cutoff, 100)).thenReturn(List.of(old));
        when(messageAttachmentRepository.findByMessageIdIn(List.of(messageId))).thenReturn(List.of(attachment));
        when(chatMessageRepository.deleteByIdIn(List.of(messageId))).thenReturn(1);

        int deleted = service.purgeMessagesOlderThan(cutoff, 100);

        assertEquals(1, deleted);
        InOrder order = inOrder(chatMessageRepository, mediaStorage);
        order.verify(chatMessageRepository).deleteByIdIn(List.of(messageId));
        order.verify(mediaStorage).deleteQuietly("kchat/rooms/" + roomId + "/file.jpg");
    }

    @Test
    void purgeMessagesOlderThan_noOpWhenEmpty() {
        Instant cutoff = Instant.parse("2025-01-01T00:00:00Z");
        when(chatMessageRepository.findOlderThanForRetentionCleanup(cutoff, 50)).thenReturn(List.of());

        assertEquals(0, service.purgeMessagesOlderThan(cutoff, 50));

        verify(chatMessageRepository, never()).deleteByIdIn(any());
        verify(mediaStorage, never()).deleteQuietly(any());
    }

    @Test
    void purgeMessagesOlderThan_skipsWhenBatchSizeZero() {
        assertEquals(0, service.purgeMessagesOlderThan(Instant.now(), 0));
        verify(chatMessageRepository, never()).findOlderThanForRetentionCleanup(any(), anyInt());
    }

    @Test
    void purgeMessagesOlderThan_capsBatchSize() {
        Instant cutoff = Instant.parse("2025-01-01T00:00:00Z");
        when(chatMessageRepository.findOlderThanForRetentionCleanup(
                eq(cutoff),
                eq(MessageRetentionCleanupServiceImpl.MAX_BATCH_SIZE)
        )).thenReturn(List.of());

        assertEquals(0, service.purgeMessagesOlderThan(cutoff, 50_000));

        verify(chatMessageRepository).findOlderThanForRetentionCleanup(
                cutoff,
                MessageRetentionCleanupServiceImpl.MAX_BATCH_SIZE
        );
    }

    private static ExpiredMessageRow row(UUID id, UUID roomId) {
        return new ExpiredMessageRow() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public UUID getRoomId() {
                return roomId;
            }
        };
    }
}
