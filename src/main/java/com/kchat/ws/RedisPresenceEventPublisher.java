package com.kchat.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kchat.common.dto.ws.WsEnvelope;
import com.kchat.config.RedisChannels;
import com.kchat.repository.RoomMemberRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisPresenceEventPublisher implements PresenceEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(RedisPresenceEventPublisher.class);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final RoomMemberRepository roomMemberRepository;

  public RedisPresenceEventPublisher(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      RoomMemberRepository roomMemberRepository) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.roomMemberRepository = roomMemberRepository;
  }

  @Override
  public void presenceChanged(UUID userId, boolean online) {
    try {
      List<UUID> recipients = roomMemberRepository.findPeerUserIds(userId);
      WsEnvelope envelope =
          WsEnvelope.presence(
              new WsEnvelope.PresencePayload(userId.toString(), online, recipients));
      redis.convertAndSend(RedisChannels.PRESENCE, objectMapper.writeValueAsString(envelope));
    } catch (JsonProcessingException ex) {
      log.warn("Failed to publish presence for {}: {}", userId, ex.getMessage());
    }
  }
}
