package com.kchat.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kchat.common.dto.chat.MessageDto;
import com.kchat.common.dto.ws.WsEnvelope;
import com.kchat.common.dto.ws.WsEnvelope.MessageNewPayload;
import com.kchat.config.RedisChannels;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisMessageEventPublisher implements MessageEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(RedisMessageEventPublisher.class);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final com.kchat.service.MessagePushNotifier messagePushNotifier;

  public RedisMessageEventPublisher(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      com.kchat.service.MessagePushNotifier messagePushNotifier) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.messagePushNotifier = messagePushNotifier;
  }

  @Override
  public void messageCreated(UUID roomId, UUID senderId, List<UUID> memberIds, MessageDto message) {
    publishMessageEvent(WsEnvelope.TYPE_MESSAGE_NEW, roomId, senderId, memberIds, message);
    messagePushNotifier.onMessageCreated(roomId, senderId, memberIds, message);
  }

  @Override
  public void messageUpdated(UUID roomId, UUID senderId, List<UUID> memberIds, MessageDto message) {
    publishMessageEvent(WsEnvelope.TYPE_MESSAGE_UPDATED, roomId, senderId, memberIds, message);
  }

  @Override
  public void messageDeleted(UUID roomId, String messageId, List<UUID> memberIds) {
    if (memberIds == null || memberIds.isEmpty()) {
      return;
    }
    WsEnvelope envelope =
        WsEnvelope.messageDeleted(
            new WsEnvelope.MessageDeletedPayload(roomId.toString(), messageId, memberIds));
    try {
      redis.convertAndSend(
          RedisChannels.roomChannel(roomId), objectMapper.writeValueAsString(envelope));
    } catch (JsonProcessingException ex) {
      log.error("Failed to publish message_deleted for room {}", roomId, ex);
    }
  }

  @Override
  public void messagesRead(
      UUID roomId, UUID readerId, String upToMessageId, long upToCreatedAt, List<UUID> memberIds) {
    if (memberIds == null || memberIds.isEmpty()) {
      return;
    }
    WsEnvelope envelope =
        WsEnvelope.messagesRead(
            new WsEnvelope.MessagesReadPayload(
                roomId.toString(), readerId, upToMessageId, upToCreatedAt, memberIds));
    try {
      redis.convertAndSend(
          RedisChannels.roomChannel(roomId), objectMapper.writeValueAsString(envelope));
    } catch (JsonProcessingException ex) {
      log.error("Failed to publish messages_read for room {}", roomId, ex);
    }
  }

  private void publishMessageEvent(
      String type, UUID roomId, UUID senderId, List<UUID> memberIds, MessageDto message) {
    if (memberIds == null || memberIds.isEmpty()) {
      log.warn("Skip {} publish for room {}: no members", type, roomId);
      return;
    }
    MessageDto canonical = MessageDtoCopy.withMine(message, false);
    WsEnvelope envelope =
        new WsEnvelope(
            type, new MessageNewPayload(roomId.toString(), senderId, memberIds, canonical));
    try {
      redis.convertAndSend(
          RedisChannels.roomChannel(roomId), objectMapper.writeValueAsString(envelope));
    } catch (JsonProcessingException ex) {
      log.error("Failed to publish {} for room {}", type, roomId, ex);
    }
  }
}
