package com.kchat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kchat.common.dto.ws.WsEnvelope;
import com.kchat.common.dto.ws.WsEnvelope.TypingPayload;
import com.kchat.config.RedisChannels;
import com.kchat.repository.RoomMemberRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Relays typing indicators to room peers via local WS sessions and Redis (multi-instance). */
@Service
public class TypingRelayService {

  private static final Logger log = LoggerFactory.getLogger(TypingRelayService.class);

  private final TypingService typingService;
  private final RoomMemberRepository roomMemberRepository;
  private final WsSessionRegistry sessionRegistry;
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public TypingRelayService(
      TypingService typingService,
      RoomMemberRepository roomMemberRepository,
      WsSessionRegistry sessionRegistry,
      StringRedisTemplate redis,
      ObjectMapper objectMapper) {
    this.typingService = typingService;
    this.roomMemberRepository = roomMemberRepository;
    this.sessionRegistry = sessionRegistry;
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  public void relayTyping(UUID userId, UUID roomId, boolean typing) {
    if (roomMemberRepository.findActiveMembership(roomId, userId).isEmpty()) {
      log.debug("Ignore typing from non-member {} in room {}", userId, roomId);
      return;
    }
    try {
      if (typing) {
        typingService.markTyping(roomId, userId);
      } else {
        typingService.clearTyping(roomId, userId);
      }
    } catch (Exception ex) {
      log.debug("Typing Redis key update failed for room {}: {}", roomId, ex.getMessage());
    }

    List<UUID> memberIds = roomMemberRepository.findActiveMemberUserIds(roomId);
    String clientJson;
    try {
      clientJson = buildClientEnvelope(roomId, userId, typing);
    } catch (Exception ex) {
      log.warn("Failed to build typing envelope for room {}: {}", roomId, ex.getMessage());
      return;
    }
    fanOutLocally(userId, memberIds, clientJson);

    WsEnvelope envelope =
        WsEnvelope.typing(
            new TypingPayload(roomId.toString(), userId.toString(), typing, memberIds));
    try {
      redis.convertAndSend(
          RedisChannels.roomChannel(roomId), objectMapper.writeValueAsString(envelope));
    } catch (Exception ex) {
      log.warn("Failed to publish typing for room {}: {}", roomId, ex.getMessage());
    }
  }

  private String buildClientEnvelope(UUID roomId, UUID userId, boolean typing) throws Exception {
    ObjectNode envelope = objectMapper.createObjectNode();
    envelope.put("type", WsEnvelope.TYPE_TYPING);
    ObjectNode payload = envelope.putObject("payload");
    payload.put("room_id", roomId.toString());
    payload.put("user_id", userId.toString());
    payload.put("typing", typing);
    return objectMapper.writeValueAsString(envelope);
  }

  private void fanOutLocally(UUID senderId, List<UUID> memberIds, String clientJson) {
    for (UUID memberId : memberIds) {
      if (!memberId.equals(senderId)) {
        sessionRegistry.sendToUser(memberId, clientJson);
      }
    }
  }
}
