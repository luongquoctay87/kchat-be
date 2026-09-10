package com.kchat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kchat.common.dto.call.CallDto;
import com.kchat.common.dto.ws.WsEnvelope;
import com.kchat.common.dto.ws.WsEnvelope.CallEventPayload;
import com.kchat.common.dto.ws.WsEnvelope.IceSignalPayload;
import com.kchat.config.RedisChannels;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisCallEventPublisher implements CallEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(RedisCallEventPublisher.class);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public RedisCallEventPublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  @Override
  public void callIncoming(CallDto call, List<UUID> recipientIds) {
    publishCall(WsEnvelope.TYPE_CALL_INCOMING, call, recipientIds);
  }

  @Override
  public void callAccepted(CallDto call, List<UUID> recipientIds) {
    publishCall(WsEnvelope.TYPE_CALL_ACCEPTED, call, recipientIds);
  }

  @Override
  public void callRejected(CallDto call, List<UUID> recipientIds) {
    publishCall(WsEnvelope.TYPE_CALL_REJECTED, call, recipientIds);
  }

  @Override
  public void callEnded(CallDto call, List<UUID> recipientIds) {
    publishCall(WsEnvelope.TYPE_CALL_ENDED, call, recipientIds);
  }

  @Override
  public void iceOffer(
      String callId, String roomId, UUID fromUserId, String sdp, List<UUID> recipientIds) {
    publishIce(
        WsEnvelope.TYPE_ICE_OFFER, callId, roomId, fromUserId, sdp, null, null, null, recipientIds);
  }

  @Override
  public void iceAnswer(
      String callId, String roomId, UUID fromUserId, String sdp, List<UUID> recipientIds) {
    publishIce(
        WsEnvelope.TYPE_ICE_ANSWER,
        callId,
        roomId,
        fromUserId,
        sdp,
        null,
        null,
        null,
        recipientIds);
  }

  @Override
  public void iceCandidate(
      String callId,
      String roomId,
      UUID fromUserId,
      String candidate,
      String sdpMid,
      Integer sdpMLineIndex,
      List<UUID> recipientIds) {
    publishIce(
        WsEnvelope.TYPE_ICE_CANDIDATE,
        callId,
        roomId,
        fromUserId,
        null,
        candidate,
        sdpMid,
        sdpMLineIndex,
        recipientIds);
  }

  private void publishCall(String type, CallDto call, List<UUID> recipientIds) {
    if (recipientIds == null || recipientIds.isEmpty()) {
      return;
    }
    WsEnvelope envelope = WsEnvelope.callEvent(type, new CallEventPayload(call, recipientIds));
    publish(UUID.fromString(call.roomId()), envelope, type);
  }

  private void publishIce(
      String type,
      String callId,
      String roomId,
      UUID fromUserId,
      String sdp,
      String candidate,
      String sdpMid,
      Integer sdpMLineIndex,
      List<UUID> recipientIds) {
    if (recipientIds == null || recipientIds.isEmpty()) {
      return;
    }
    WsEnvelope envelope =
        WsEnvelope.iceSignal(
            type,
            new IceSignalPayload(
                callId,
                roomId,
                fromUserId.toString(),
                sdp,
                candidate,
                sdpMid,
                sdpMLineIndex,
                recipientIds));
    publish(UUID.fromString(roomId), envelope, type);
  }

  private void publish(UUID roomId, WsEnvelope envelope, String type) {
    try {
      redis.convertAndSend(
          RedisChannels.roomChannel(roomId), objectMapper.writeValueAsString(envelope));
    } catch (Exception ex) {
      log.error("Failed to publish {} for room {}", type, roomId, ex);
    }
  }
}
