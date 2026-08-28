package com.kchat.ws;

import com.kchat.common.dto.call.CallDto;
import java.util.List;
import java.util.UUID;

public interface CallEventPublisher {

    void callIncoming(CallDto call, List<UUID> recipientIds);

    void callAccepted(CallDto call, List<UUID> recipientIds);

    void callRejected(CallDto call, List<UUID> recipientIds);

    void callEnded(CallDto call, List<UUID> recipientIds);

    void iceOffer(String callId, String roomId, UUID fromUserId, String sdp, List<UUID> recipientIds);

    void iceAnswer(String callId, String roomId, UUID fromUserId, String sdp, List<UUID> recipientIds);

    void iceCandidate(
            String callId,
            String roomId,
            UUID fromUserId,
            String candidate,
            String sdpMid,
            Integer sdpMLineIndex,
            List<UUID> recipientIds
    );
}
