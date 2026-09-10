package com.kchat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kchat.entity.MessageAttachment;
import com.kchat.media.MediaStorage;
import com.kchat.repository.ChatMessageRepository;
import com.kchat.repository.ExpiredMessageRow;
import com.kchat.repository.MessageAttachmentRepository;
import com.kchat.repository.RoomMemberRepository;
import com.kchat.ws.MessageEventPublisher;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisappearingMessageCleanupServiceImplTest {

  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private MessageAttachmentRepository messageAttachmentRepository;
  @Mock private RoomMemberRepository roomMemberRepository;
  @Mock private MediaStorage mediaStorage;
  @Mock private MessageEventPublisher messageEventPublisher;

  private DisappearingMessageCleanupServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new DisappearingMessageCleanupServiceImpl(
            chatMessageRepository,
            messageAttachmentRepository,
            roomMemberRepository,
            mediaStorage,
            messageEventPublisher);
  }

  @Test
  void purgeExpiredMessages_deletesDbThenMediaThenNotifies() {
    UUID roomId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();

    ExpiredMessageRow expired = row(messageId, roomId);
    MessageAttachment attachment = new MessageAttachment();
    attachment.setS3Key("room/file.jpg");

    when(chatMessageRepository.findExpiredForDisappearingCleanup(100)).thenReturn(List.of(expired));
    when(messageAttachmentRepository.findByMessageIdIn(List.of(messageId)))
        .thenReturn(List.of(attachment));
    when(chatMessageRepository.deleteByIdIn(List.of(messageId))).thenReturn(1);
    when(roomMemberRepository.findActiveMemberUserIds(roomId)).thenReturn(List.of(memberId));

    int deleted = service.purgeExpiredMessages(100);

    assertEquals(1, deleted);
    InOrder order = inOrder(chatMessageRepository, mediaStorage, messageEventPublisher);
    order.verify(chatMessageRepository).deleteByIdIn(List.of(messageId));
    order.verify(mediaStorage).deleteQuietly("room/file.jpg");
    order
        .verify(messageEventPublisher)
        .messageDeleted(roomId, messageId.toString(), List.of(memberId));
  }

  @Test
  void purgeExpiredMessages_noOpWhenEmpty() {
    when(chatMessageRepository.findExpiredForDisappearingCleanup(50)).thenReturn(List.of());

    assertEquals(0, service.purgeExpiredMessages(50));

    verify(chatMessageRepository, never()).deleteByIdIn(any());
    verify(messageEventPublisher, never()).messageDeleted(any(), any(), any());
  }

  @Test
  void purgeExpiredMessages_skipsWhenBatchSizeZero() {
    assertEquals(0, service.purgeExpiredMessages(0));
    verify(chatMessageRepository, never()).findExpiredForDisappearingCleanup(anyInt());
  }

  @Test
  void purgeExpiredMessages_capsBatchSize() {
    when(chatMessageRepository.findExpiredForDisappearingCleanup(
            DisappearingMessageCleanupServiceImpl.MAX_BATCH_SIZE))
        .thenReturn(List.of());

    assertEquals(0, service.purgeExpiredMessages(50_000));

    verify(chatMessageRepository)
        .findExpiredForDisappearingCleanup(DisappearingMessageCleanupServiceImpl.MAX_BATCH_SIZE);
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
