package com.kchat.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kchat.common.dto.ws.WsEnvelope;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Receives Redis room/presence events (possibly from another instance) and fans out to local WS
 * sessions.
 */
@Component
public class RedisRoomFanoutListener implements MessageListener {

  private static final Logger log = LoggerFactory.getLogger(RedisRoomFanoutListener.class);

  private final ObjectMapper objectMapper;
  private final WsSessionRegistry sessionRegistry;

  public RedisRoomFanoutListener(ObjectMapper objectMapper, WsSessionRegistry sessionRegistry) {
    this.objectMapper = objectMapper;
    this.sessionRegistry = sessionRegistry;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String body = new String(message.getBody(), StandardCharsets.UTF_8);
    try {
      JsonNode root = objectMapper.readTree(body);
      String type = root.path("type").asText();
      if (WsEnvelope.TYPE_MESSAGE_NEW.equals(type)
          || WsEnvelope.TYPE_MESSAGE_UPDATED.equals(type)) {
        fanOutMessageEvent(type, root.path("payload"));
      } else if (WsEnvelope.TYPE_MESSAGE_DELETED.equals(type)) {
        fanOutMessageDeleted(root.path("payload"));
      } else if (WsEnvelope.TYPE_MESSAGES_READ.equals(type)) {
        fanOutMessagesRead(root);
      } else if (WsEnvelope.TYPE_PRESENCE.equals(type)) {
        fanOutPresence(root);
      } else if (WsEnvelope.TYPE_TYPING.equals(type)) {
        fanOutTyping(root);
      } else if (isCallOrIceType(type)) {
        fanOutToRecipients(root);
      }
    } catch (Exception ex) {
      log.warn("Failed to handle Redis WS event: {}", ex.getMessage());
    }
  }

  private static boolean isCallOrIceType(String type) {
    return WsEnvelope.TYPE_CALL_INCOMING.equals(type)
        || WsEnvelope.TYPE_CALL_ACCEPTED.equals(type)
        || WsEnvelope.TYPE_CALL_REJECTED.equals(type)
        || WsEnvelope.TYPE_CALL_ENDED.equals(type)
        || WsEnvelope.TYPE_ICE_OFFER.equals(type)
        || WsEnvelope.TYPE_ICE_ANSWER.equals(type)
        || WsEnvelope.TYPE_ICE_CANDIDATE.equals(type);
  }

  private void fanOutToRecipients(JsonNode root) throws Exception {
    JsonNode payload = root.path("payload");
    JsonNode recipients = payload.path("recipient_ids");
    if (!recipients.isArray() || recipients.isEmpty()) {
      return;
    }
    String type = root.path("type").asText();
    // Strip recipient_ids before sending to clients.
    ObjectNode envelope = objectMapper.createObjectNode();
    envelope.put("type", type);
    ObjectNode outPayload = payload.deepCopy();
    outPayload.remove("recipient_ids");
    envelope.set("payload", outPayload);
    String json = objectMapper.writeValueAsString(envelope);
    for (JsonNode node : recipients) {
      try {
        UUID recipientId = UUID.fromString(node.asText());
        boolean hasWs = sessionRegistry.hasSessions(recipientId);
        log.info("Dispatching WS {} to recipient {} (hasWsSession={})", type, recipientId, hasWs);
        sessionRegistry.sendToUser(recipientId, json);
      } catch (IllegalArgumentException ignored) {
        // skip
      }
    }
  }

  private void fanOutMessageEvent(String type, JsonNode payload) throws Exception {
    String senderRaw = payload.path("sender_id").asText(null);
    UUID senderId = null;
    if (senderRaw != null && !senderRaw.isBlank()) {
      try {
        senderId = UUID.fromString(senderRaw);
      } catch (IllegalArgumentException ex) {
        return;
      }
    }
    // null senderId = system / bot / call_event — fan out to all members as not-mine
    JsonNode memberIds = payload.path("member_ids");
    JsonNode messageNode = payload.path("message");
    if (!memberIds.isArray() || messageNode.isMissingNode()) {
      return;
    }

    String roomId = payload.path("room_id").asText();
    for (JsonNode memberNode : memberIds) {
      UUID memberId;
      try {
        memberId = UUID.fromString(memberNode.asText());
      } catch (IllegalArgumentException ex) {
        continue;
      }
      if (!sessionRegistry.hasSessions(memberId)) {
        continue;
      }
      boolean mine = senderId != null && memberId.equals(senderId);
      ObjectNode envelope = objectMapper.createObjectNode();
      envelope.put("type", type);
      ObjectNode outPayload = envelope.putObject("payload");
      outPayload.put("room_id", roomId);
      ObjectNode msg = messageNode.deepCopy();
      msg.put("is_mine", mine);
      if (mine) {
        msg.putNull("sender_name");
      }
      outPayload.set("message", msg);
      sessionRegistry.sendToUser(memberId, objectMapper.writeValueAsString(envelope));
    }
  }

  private void fanOutMessageDeleted(JsonNode payload) throws Exception {
    JsonNode memberIds = payload.path("member_ids");
    if (!memberIds.isArray()) {
      return;
    }
    String roomId = payload.path("room_id").asText();
    String messageId = payload.path("message_id").asText();
    ObjectNode envelope = objectMapper.createObjectNode();
    envelope.put("type", WsEnvelope.TYPE_MESSAGE_DELETED);
    ObjectNode outPayload = envelope.putObject("payload");
    outPayload.put("room_id", roomId);
    outPayload.put("message_id", messageId);

    String json = objectMapper.writeValueAsString(envelope);
    for (JsonNode memberNode : memberIds) {
      try {
        UUID memberId = UUID.fromString(memberNode.asText());
        sessionRegistry.sendToUser(memberId, json);
      } catch (IllegalArgumentException ignored) {
        // skip bad id
      }
    }
  }

  private void fanOutMessagesRead(JsonNode root) throws Exception {
    JsonNode payload = root.path("payload");
    JsonNode memberIds = payload.path("member_ids");
    if (!memberIds.isArray()) {
      return;
    }
    String readerRaw = payload.path("reader_id").asText(null);
    UUID readerId = null;
    if (readerRaw != null && !readerRaw.isBlank()) {
      try {
        readerId = UUID.fromString(readerRaw);
      } catch (IllegalArgumentException ignored) {
        // keep null
      }
    }
    // Strip member_ids — clients only need room/reader/up_to fields.
    ObjectNode envelope = objectMapper.createObjectNode();
    envelope.put("type", WsEnvelope.TYPE_MESSAGES_READ);
    ObjectNode outPayload = envelope.putObject("payload");
    outPayload.put("room_id", payload.path("room_id").asText());
    outPayload.put("reader_id", readerRaw);
    outPayload.put("up_to_message_id", payload.path("up_to_message_id").asText());
    outPayload.put("up_to_created_at", payload.path("up_to_created_at").asLong());
    String json = objectMapper.writeValueAsString(envelope);
    for (JsonNode memberNode : memberIds) {
      try {
        UUID memberId = UUID.fromString(memberNode.asText());
        if (readerId != null && memberId.equals(readerId)) {
          continue;
        }
        sessionRegistry.sendToUser(memberId, json);
      } catch (IllegalArgumentException ignored) {
        // skip
      }
    }
  }

  private void fanOutPresence(JsonNode root) throws Exception {
    JsonNode payload = root.path("payload");
    JsonNode recipients = payload.path("recipient_ids");
    String json = objectMapper.writeValueAsString(root);
    if (recipients.isArray() && !recipients.isEmpty()) {
      for (JsonNode node : recipients) {
        try {
          UUID recipientId = UUID.fromString(node.asText());
          sessionRegistry.sendToUser(recipientId, json);
        } catch (IllegalArgumentException ignored) {
          // skip bad id
        }
      }
      return;
    }
    // Backward-compatible: no recipient list → local peers only (exclude subject)
    UUID subject = UUID.fromString(payload.path("user_id").asText());
    for (UUID userId : sessionRegistry.onlineUserIds()) {
      if (!userId.equals(subject)) {
        sessionRegistry.sendToUser(userId, json);
      }
    }
  }

  private void fanOutTyping(JsonNode root) throws Exception {
    JsonNode payload = root.path("payload");
    String senderRaw = textOrNull(payload, "user_id");
    if (senderRaw == null) {
      senderRaw = textOrNull(payload, "userId");
    }
    UUID senderId = null;
    if (senderRaw != null && !senderRaw.isBlank()) {
      try {
        senderId = UUID.fromString(senderRaw);
      } catch (IllegalArgumentException ignored) {
        // keep null — still fan out below
      }
    }
    String roomId = textOrNull(payload, "room_id");
    if (roomId == null) {
      roomId = textOrNull(payload, "roomId");
    }
    if (roomId == null || roomId.isBlank()) {
      return;
    }
    ObjectNode envelope = objectMapper.createObjectNode();
    envelope.put("type", WsEnvelope.TYPE_TYPING);
    ObjectNode outPayload = envelope.putObject("payload");
    outPayload.put("room_id", roomId);
    if (senderRaw != null && !senderRaw.isBlank()) {
      outPayload.put("user_id", senderRaw);
    }
    outPayload.put("typing", payload.path("typing").asBoolean(true));
    String json = objectMapper.writeValueAsString(envelope);

    JsonNode memberIds = payload.path("member_ids");
    if (!memberIds.isArray() || memberIds.isEmpty()) {
      memberIds = payload.path("memberIds");
    }
    if (memberIds.isArray() && !memberIds.isEmpty()) {
      for (JsonNode node : memberIds) {
        try {
          UUID memberId = UUID.fromString(node.asText());
          if (senderId == null || !memberId.equals(senderId)) {
            sessionRegistry.sendToUser(memberId, json);
          }
        } catch (IllegalArgumentException ignored) {
          // skip
        }
      }
      return;
    }
    if (senderId == null) {
      return;
    }
    for (UUID userId : sessionRegistry.onlineUserIds()) {
      if (!userId.equals(senderId)) {
        sessionRegistry.sendToUser(userId, json);
      }
    }
  }

  private static String textOrNull(JsonNode payload, String field) {
    String value = payload.path(field).asText(null);
    return value == null || value.isBlank() ? null : value;
  }
}
