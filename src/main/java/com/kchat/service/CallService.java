package com.kchat.service;

import com.kchat.common.dto.call.CallDto;
import java.util.UUID;

public interface CallService {

  CallDto initiate(UUID userId, UUID roomId, String callTypeRaw);

  CallDto accept(UUID userId, UUID callId);

  CallDto decline(UUID userId, UUID callId);

  CallDto end(UUID userId, UUID callId);

  void relayIceOffer(UUID userId, UUID callId, String sdp);

  void relayIceAnswer(UUID userId, UUID callId, String sdp);

  void relayIceCandidate(
      UUID userId, UUID callId, String candidate, String sdpMid, Integer sdpMLineIndex);

  /** Mark ringing calls older than timeout as missed. */
  int expireRingingCalls();

  /** Ringing calls where this user is callee (not initiator). */
  java.util.List<CallDto> listIncomingRinging(UUID userId);
}
