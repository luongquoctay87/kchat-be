package com.kchat.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kchat.common.dto.ws.WsEnvelope;
import com.kchat.service.CallService;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

  private final WsSessionRegistry sessionRegistry;
  private final PresenceService presenceService;
  private final PresenceEventPublisher presenceEventPublisher;
  private final TypingRelayService typingRelayService;
  private final CallService callService;
  private final ObjectMapper objectMapper;

  public ChatWebSocketHandler(
      WsSessionRegistry sessionRegistry,
      PresenceService presenceService,
      PresenceEventPublisher presenceEventPublisher,
      TypingRelayService typingRelayService,
      CallService callService,
      ObjectMapper objectMapper) {
    this.sessionRegistry = sessionRegistry;
    this.presenceService = presenceService;
    this.presenceEventPublisher = presenceEventPublisher;
    this.typingRelayService = typingRelayService;
    this.callService = callService;
    this.objectMapper = objectMapper;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    UUID userId = userId(session);
    sessionRegistry.add(userId, session);
    boolean newlyOnline = presenceService.markOnline(userId);
    if (newlyOnline && presenceService.canShowOnline(userId)) {
      presenceEventPublisher.presenceChanged(userId, true);
    }
    log.info("WS connected user={} session={}", userId, session.getId());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    UUID userId = userId(session);
    sessionRegistry.remove(userId, session);
    if (!sessionRegistry.hasSessions(userId)) {
      goOffline(userId);
    }
    log.info("WS closed user={} session={} status={}", userId, session.getId(), status);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message)
      throws IOException {
    String payload = message.getPayload();
    if (payload == null || payload.isBlank()) {
      return;
    }
    String trimmed = payload.trim();
    if ("ping".equalsIgnoreCase(trimmed)) {
      session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
      return;
    }
    try {
      JsonNode node = objectMapper.readTree(trimmed);
      String type = node.path("type").asText();
      if ("ping".equalsIgnoreCase(type)) {
        session.sendMessage(
            new TextMessage(
                objectMapper.writeValueAsString(new WsEnvelope(WsEnvelope.TYPE_PONG, null))));
        return;
      }
      if (WsEnvelope.TYPE_TYPING.equals(type)) {
        handleTyping(userId(session), node.path("payload"));
        return;
      }
      if (isIceType(type)) {
        handleIce(userId(session), type, node.path("payload"));
      }
    } catch (Exception ignored) {
      // ignore malformed client frames
    }
  }

  private static boolean isIceType(String type) {
    return WsEnvelope.TYPE_ICE_OFFER.equals(type)
        || WsEnvelope.TYPE_ICE_ANSWER.equals(type)
        || WsEnvelope.TYPE_ICE_CANDIDATE.equals(type);
  }

  private void handleIce(UUID userId, String type, JsonNode payload) {
    String callRaw = payload.path("call_id").asText(null);
    if (callRaw == null || callRaw.isBlank()) {
      return;
    }
    UUID callId;
    try {
      callId = UUID.fromString(callRaw);
    } catch (IllegalArgumentException ex) {
      return;
    }
    try {
      if (WsEnvelope.TYPE_ICE_OFFER.equals(type)) {
        callService.relayIceOffer(userId, callId, payload.path("sdp").asText(null));
      } else if (WsEnvelope.TYPE_ICE_ANSWER.equals(type)) {
        callService.relayIceAnswer(userId, callId, payload.path("sdp").asText(null));
      } else if (WsEnvelope.TYPE_ICE_CANDIDATE.equals(type)) {
        Integer mLine =
            payload.hasNonNull("sdp_m_line_index")
                ? payload.path("sdp_m_line_index").asInt()
                : null;
        callService.relayIceCandidate(
            userId,
            callId,
            payload.path("candidate").asText(null),
            payload.path("sdp_mid").asText(null),
            mLine);
      }
    } catch (RuntimeException ex) {
      log.debug("ICE relay rejected for user {}: {}", userId, ex.getMessage());
    }
  }

  private void handleTyping(UUID userId, JsonNode payload) {
    String roomRaw = payload.path("room_id").asText(null);
    if (roomRaw == null || roomRaw.isBlank()) {
      roomRaw = payload.path("roomId").asText(null);
    }
    if (roomRaw == null || roomRaw.isBlank()) {
      return;
    }
    UUID roomId;
    try {
      roomId = UUID.fromString(roomRaw);
    } catch (IllegalArgumentException ex) {
      return;
    }
    boolean typing = !payload.has("typing") || payload.path("typing").asBoolean(true);
    typingRelayService.relayTyping(userId, roomId, typing);
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.debug("WS transport error session={}: {}", session.getId(), exception.getMessage());
    try {
      UUID userId = userId(session);
      sessionRegistry.remove(userId, session);
      if (!sessionRegistry.hasSessions(userId)) {
        goOffline(userId);
      }
    } catch (Exception ignored) {
      // session may lack attributes
    }
  }

  private void goOffline(UUID userId) {
    presenceService.markOffline(userId);
    try {
      presenceService.touchLastSeen(userId);
    } catch (Exception ex) {
      log.debug("Failed to update last_seen for {}: {}", userId, ex.getMessage());
    }
    presenceEventPublisher.presenceChanged(userId, false);
  }

  private static UUID userId(WebSocketSession session) {
    Object value = session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_ID);
    if (value instanceof UUID uuid) {
      return uuid;
    }
    throw new IllegalStateException("WebSocket session missing userId");
  }
}
