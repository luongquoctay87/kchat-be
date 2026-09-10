package com.kchat.common.dto.ws;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kchat.common.dto.call.CallDto;
import com.kchat.common.dto.chat.MessageDto;
import java.util.List;
import java.util.UUID;

public record WsEnvelope(String type, Object payload) {

  public static final String TYPE_MESSAGE_NEW = "message_new";
  public static final String TYPE_MESSAGE_UPDATED = "message_updated";
  public static final String TYPE_MESSAGE_DELETED = "message_deleted";
  public static final String TYPE_MESSAGES_READ = "messages_read";
  public static final String TYPE_PRESENCE = "presence";
  public static final String TYPE_TYPING = "typing";
  public static final String TYPE_PONG = "pong";

  public static final String TYPE_CALL_INCOMING = "call_incoming";
  public static final String TYPE_CALL_ACCEPTED = "call_accepted";
  public static final String TYPE_CALL_REJECTED = "call_rejected";
  public static final String TYPE_CALL_ENDED = "call_ended";
  public static final String TYPE_ICE_OFFER = "ice_offer";
  public static final String TYPE_ICE_ANSWER = "ice_answer";
  public static final String TYPE_ICE_CANDIDATE = "ice_candidate";
  public static final String TYPE_DEVICE_SESSION_REVOKED = "device_session_revoked";

  public static WsEnvelope messageNew(MessageNewPayload payload) {
    return new WsEnvelope(TYPE_MESSAGE_NEW, payload);
  }

  public static WsEnvelope messageUpdated(MessageNewPayload payload) {
    return new WsEnvelope(TYPE_MESSAGE_UPDATED, payload);
  }

  public static WsEnvelope messageDeleted(MessageDeletedPayload payload) {
    return new WsEnvelope(TYPE_MESSAGE_DELETED, payload);
  }

  public static WsEnvelope messagesRead(MessagesReadPayload payload) {
    return new WsEnvelope(TYPE_MESSAGES_READ, payload);
  }

  public static WsEnvelope presence(PresencePayload payload) {
    return new WsEnvelope(TYPE_PRESENCE, payload);
  }

  public static WsEnvelope typing(TypingPayload payload) {
    return new WsEnvelope(TYPE_TYPING, payload);
  }

  public static WsEnvelope callEvent(String type, CallEventPayload payload) {
    return new WsEnvelope(type, payload);
  }

  public static WsEnvelope iceSignal(String type, IceSignalPayload payload) {
    return new WsEnvelope(type, payload);
  }

  public static WsEnvelope deviceSessionRevoked(DeviceSessionRevokedPayload payload) {
    return new WsEnvelope(TYPE_DEVICE_SESSION_REVOKED, payload);
  }

  public record DeviceSessionRevokedPayload(@JsonProperty("device_token") String deviceToken) {}

  public record MessageNewPayload(
      String roomId, UUID senderId, List<UUID> memberIds, MessageDto message) {}

  public record MessageDeletedPayload(String roomId, String messageId, List<UUID> memberIds) {}

  public record MessagesReadPayload(
      String roomId,
      UUID readerId,
      String upToMessageId,
      long upToCreatedAt,
      List<UUID> memberIds) {}

  public record PresencePayload(String userId, boolean online, List<UUID> recipientIds) {
    public PresencePayload(String userId, boolean online) {
      this(userId, online, List.of());
    }
  }

  public record TypingPayload(
      @JsonProperty("room_id") String roomId,
      @JsonProperty("user_id") String userId,
      @JsonProperty("typing") boolean typing,
      @JsonProperty("member_ids") List<UUID> memberIds) {}

  public record CallEventPayload(CallDto call, List<UUID> recipientIds) {}

  public record IceSignalPayload(
      String callId,
      String roomId,
      String fromUserId,
      String sdp,
      String candidate,
      String sdpMid,
      Integer sdpMLineIndex,
      List<UUID> recipientIds) {}
}
