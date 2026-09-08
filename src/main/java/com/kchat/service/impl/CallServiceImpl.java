package com.kchat.service.impl;

import com.kchat.common.dto.call.CallDto;
import com.kchat.common.dto.chat.MessageDto;
import com.kchat.common.enums.CallMediaType;
import com.kchat.common.enums.CallStatus;
import com.kchat.common.enums.MessageType;
import com.kchat.common.enums.RoomType;
import com.kchat.common.exception.ApiException;
import com.kchat.entity.CallParticipant;
import com.kchat.entity.CallSession;
import com.kchat.entity.ChatMessage;
import com.kchat.entity.ChatRoom;
import com.kchat.entity.RoomMember;
import com.kchat.entity.User;
import com.kchat.entity.UserDevice;
import com.kchat.repository.CallParticipantRepository;
import com.kchat.repository.CallSessionRepository;
import com.kchat.repository.ChatMessageRepository;
import com.kchat.repository.RoomMemberRepository;
import com.kchat.repository.UserDeviceRepository;
import com.kchat.repository.UserRepository;
import com.kchat.service.CallService;
import com.kchat.service.FcmPushService;
import com.kchat.service.mapper.ChatMapper;
import com.kchat.ws.CallEventPublisher;
import com.kchat.ws.MessageEventPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional
public class CallServiceImpl implements CallService {

    private static final Logger log = LoggerFactory.getLogger(CallServiceImpl.class);

    static final Duration RING_TIMEOUT = Duration.ofSeconds(45);
    /** Abandoned active calls (app killed / media failed without hangup). */
    static final Duration ACTIVE_STALE_TIMEOUT = Duration.ofMinutes(15);
    private static final List<CallStatus> LIVE = List.of(CallStatus.ringing, CallStatus.active);

    private final CallSessionRepository callSessionRepository;
    private final CallParticipantRepository callParticipantRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CallEventPublisher callEventPublisher;
    private final MessageEventPublisher messageEventPublisher;
    private final UserDeviceRepository userDeviceRepository;
    private final FcmPushService fcmPushService;

    public CallServiceImpl(
            CallSessionRepository callSessionRepository,
            CallParticipantRepository callParticipantRepository,
            RoomMemberRepository roomMemberRepository,
            UserRepository userRepository,
            ChatMessageRepository chatMessageRepository,
            CallEventPublisher callEventPublisher,
            MessageEventPublisher messageEventPublisher,
            UserDeviceRepository userDeviceRepository,
            FcmPushService fcmPushService
    ) {
        this.callSessionRepository = callSessionRepository;
        this.callParticipantRepository = callParticipantRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.callEventPublisher = callEventPublisher;
        this.messageEventPublisher = messageEventPublisher;
        this.userDeviceRepository = userDeviceRepository;
        this.fcmPushService = fcmPushService;
    }

    @Override
    public CallDto initiate(UUID userId, UUID roomId, String callTypeRaw) {
        CallMediaType callType = parseCallType(callTypeRaw);
        RoomMember membership = requireMembership(roomId, userId);
        ChatRoom room = membership.getRoom();
        if (room.getType() != RoomType.direct) {
            throw ApiException.badRequest("validation_error", "Calls are only supported in 1-1 chats");
        }

        restoreLeftDirectMemberships(roomId);

        // Clear leftover live calls so a new dial works after force-stop / failed media
        // left status=active|ringing (including orphaned room rows without participants).
        supersedeLiveCallsForUser(userId);
        for (CallSession existing : callSessionRepository.findByRoomIdAndStatusIn(roomId, LIVE)) {
            supersedeCall(existing);
        }

        List<UUID> memberIds = roomMemberRepository.findActiveMemberUserIds(roomId);
        if (memberIds.size() != 2) {
            throw ApiException.badRequest("validation_error", "Direct room must have exactly 2 members");
        }
        UUID calleeId = memberIds.stream().filter(id -> !id.equals(userId)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("validation_error", "Callee not found"));

        // Same-room leftovers for the callee are already cleared above. Only block if they
        // are truly mid-call in another room.
        if (callSessionRepository.existsLiveCallForUser(calleeId, LIVE)) {
            throw ApiException.badRequest("call_busy", "A participant is already in another call");
        }

        User initiator = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));
        User callee = userRepository.findById(calleeId)
                .orElseThrow(() -> ApiException.notFound("Callee not found"));

        CallSession call = new CallSession();
        call.setRoom(room);
        call.setInitiator(initiator);
        call.setCallType(callType);
        call.setStatus(CallStatus.ringing);
        try {
            callSessionRepository.saveAndFlush(call);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.badRequest("call_busy", "A call is already in progress in this room");
        }

        saveParticipant(call.getId(), userId, Instant.now());
        saveParticipant(call.getId(), calleeId, null);

        CallDto dto = toDto(call, callee);
        log.info("Call initiated: callId={} caller={} callee={} room={} type={}",
                call.getId(), userId, calleeId, roomId, callType);
        afterCommit(() -> {
            callEventPublisher.callIncoming(dto, List.of(calleeId));
            sendCallIncomingPush(call, calleeId);
        });
        return dto;
    }

    @Override
    public CallDto accept(UUID userId, UUID callId) {
        CallSession call = requireParticipantCall(callId, userId);
        if (call.getInitiator().getId().equals(userId)) {
            throw ApiException.badRequest("validation_error", "Caller cannot accept own call");
        }
        Instant now = Instant.now();
        int updated = callSessionRepository.transitionStatus(
                callId, CallStatus.ringing, CallStatus.active, now);
        if (updated == 0) {
            throw ApiException.badRequest("call_state", "Call is not ringing");
        }
        CallParticipant participant = callParticipantRepository
                .findById(new CallParticipant.Pk(callId, userId))
                .orElseThrow(() -> ApiException.notFound("Participant not found"));
        participant.setJoinedAt(now);
        callParticipantRepository.save(participant);

        log.info("Call accepted: callId={} user={}", callId, userId);
        CallSession fresh = requireParticipantCall(callId, userId);
        CallDto dto = toDto(fresh, resolveCallee(fresh));
        // Notify all participants (including actor) so other devices leave ringing UI.
        List<UUID> recipients = new ArrayList<>(
                callParticipantRepository.findUserIdsByCallId(callId));
        afterCommit(() -> callEventPublisher.callAccepted(dto, recipients));
        return dto;
    }

    @Override
    public CallDto decline(UUID userId, UUID callId) {
        CallSession call = requireParticipantCall(callId, userId);
        if (call.getInitiator().getId().equals(userId)) {
            throw ApiException.badRequest("validation_error", "Caller should end the call instead");
        }
        Instant now = Instant.now();
        int updated = callSessionRepository.endIfStatus(
                callId, CallStatus.ringing, CallStatus.declined, now);
        if (updated == 0) {
            throw ApiException.badRequest("call_state", "Call is not ringing");
        }
        markLeft(callId, userId, now);

        log.info("Call declined: callId={} user={}", callId, userId);
        CallSession fresh = requireParticipantCall(callId, userId);
        CallDto dto = toDto(fresh, resolveCallee(fresh));
        List<UUID> recipients = new ArrayList<>(
                callParticipantRepository.findUserIdsByCallId(callId));
        afterCommit(() -> {
            callEventPublisher.callRejected(dto, recipients);
            sendCallEndedPush(callId, fresh.getInitiator().getId(), "declined");
        });
        postCallEventMessage(fresh, "Cuộc gọi bị từ chối");
        return dto;
    }

    @Override
    public CallDto end(UUID userId, UUID callId) {
        CallSession call = requireParticipantCall(callId, userId);
        CallStatus current = call.getStatus();
        if (current != CallStatus.ringing && current != CallStatus.active) {
            throw ApiException.badRequest("call_state", "Call already ended");
        }
        Instant now = Instant.now();
        boolean initiator = call.getInitiator().getId().equals(userId);

        // CAS against racing accept: try ringing first, then active.
        CallStatus endedFrom;
        CallStatus next;
        int updated = callSessionRepository.endIfStatus(
                callId, CallStatus.ringing, initiator ? CallStatus.missed : CallStatus.declined, now);
        if (updated > 0) {
            endedFrom = CallStatus.ringing;
            next = initiator ? CallStatus.missed : CallStatus.declined;
        } else {
            updated = callSessionRepository.endIfStatus(
                    callId, CallStatus.active, CallStatus.ended, now);
            if (updated == 0) {
                throw ApiException.badRequest("call_state", "Call already ended");
            }
            endedFrom = CallStatus.active;
            next = CallStatus.ended;
        }

        for (UUID participantId : callParticipantRepository.findUserIdsByCallId(callId)) {
            markLeft(callId, participantId, now);
        }

        log.info("Call ended: callId={} user={} endedFrom={} next={}", callId, userId, endedFrom, next);
        CallSession fresh = requireParticipantCall(callId, userId);
        CallDto dto = toDto(fresh, resolveCallee(fresh));
        List<UUID> recipients = new ArrayList<>(
                callParticipantRepository.findUserIdsByCallId(callId));
        afterCommit(() -> {
            callEventPublisher.callEnded(dto, recipients);
            for (UUID recipient : recipients) {
                if (!recipient.equals(userId)) {
                    sendCallEndedPush(callId, recipient, "ended");
                }
            }
        });
        if (endedFrom == CallStatus.ringing && next == CallStatus.declined) {
            postCallEventMessage(fresh, "Cuộc gọi bị từ chối");
        } else if (endedFrom == CallStatus.ringing) {
            postCallEventMessage(fresh, "Cuộc gọi nhỡ");
        } else {
            postCallEventMessage(fresh, formatEndedSummary(fresh));
        }
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public void relayIceOffer(UUID userId, UUID callId, String sdp) {
        CallSession call = requireActiveOrRinging(callId, userId);
        requireSdp(sdp);
        String roomId = call.getRoom().getId().toString();
        List<UUID> others = otherParticipants(callId, userId);
        log.info("Relaying ICE offer for call {} from user {} to {} recipient(s)", callId, userId, others.size());
        // Publish immediately — ICE is latency-sensitive and does not mutate call state.
        callEventPublisher.iceOffer(callId.toString(), roomId, userId, sdp, others);
    }

    @Override
    @Transactional(readOnly = true)
    public void relayIceAnswer(UUID userId, UUID callId, String sdp) {
        CallSession call = requireActiveOrRinging(callId, userId);
        requireSdp(sdp);
        String roomId = call.getRoom().getId().toString();
        List<UUID> others = otherParticipants(callId, userId);
        log.info("Relaying ICE answer for call {} from user {} to {} recipient(s)", callId, userId, others.size());
        callEventPublisher.iceAnswer(callId.toString(), roomId, userId, sdp, others);
    }

    @Override
    @Transactional(readOnly = true)
    public void relayIceCandidate(
            UUID userId,
            UUID callId,
            String candidate,
            String sdpMid,
            Integer sdpMLineIndex
    ) {
        CallSession call = requireActiveOrRinging(callId, userId);
        if (candidate == null || candidate.isBlank()) {
            throw ApiException.badRequest("validation_error", "candidate is required");
        }
        String roomId = call.getRoom().getId().toString();
        List<UUID> others = otherParticipants(callId, userId);
        log.info("Relaying ICE candidate for call {} from user {} to {} recipient(s)", callId, userId, others.size());
        callEventPublisher.iceCandidate(
                callId.toString(), roomId, userId, candidate, sdpMid, sdpMLineIndex, others);
    }

    @Override
    public int expireRingingCalls() {
        int count = 0;
        Instant ringCutoff = Instant.now().minus(RING_TIMEOUT);
        List<CallSession> expiredRinging = callSessionRepository.findByStatusAndCreatedAtBefore(
                CallStatus.ringing, ringCutoff);
        for (CallSession call : expiredRinging) {
            Instant now = Instant.now();
            int updated = callSessionRepository.markMissedIfStillRinging(call.getId(), now, ringCutoff);
            if (updated == 0) {
                continue;
            }
            for (UUID participantId : callParticipantRepository.findUserIdsByCallId(call.getId())) {
                markLeft(call.getId(), participantId, now);
            }
            CallSession fresh = callSessionRepository.findByIdWithRoomAndInitiator(call.getId())
                    .orElse(null);
            if (fresh == null) {
                continue;
            }
            log.info("Call ringing expired: callId={}", fresh.getId());
            CallDto dto = toDto(fresh, resolveCallee(fresh));
            List<UUID> recipients = new ArrayList<>(
                    callParticipantRepository.findUserIdsByCallId(fresh.getId()));
            UUID calleeId = resolveCallee(fresh) != null ? resolveCallee(fresh).getId() : null;
            afterCommit(() -> {
                callEventPublisher.callEnded(dto, recipients);
                if (calleeId != null) {
                    sendCallEndedPush(fresh.getId(), calleeId, "missed");
                }
            });
            postCallEventMessage(fresh, "Cuộc gọi nhỡ");
            count++;
        }

        Instant activeCutoff = Instant.now().minus(ACTIVE_STALE_TIMEOUT);
        // Expire on startedAt (media start), not createdAt — otherwise long calls die at ~15m from dial.
        List<CallSession> staleActive = callSessionRepository.findStaleActiveCalls(activeCutoff);
        for (CallSession call : staleActive) {
            if (supersedeCall(call)) {
                postCallEventMessage(call, "Cuộc gọi kết thúc");
                count++;
            }
        }
        return count;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CallDto> listIncomingRinging(UUID userId) {
        return callSessionRepository.findLiveCallsForUser(userId, List.of(CallStatus.ringing)).stream()
                .filter(call -> !call.getInitiator().getId().equals(userId))
                .map(call -> toDto(call, resolveCallee(call)))
                .toList();
    }

    private void restoreLeftDirectMemberships(UUID roomId) {
        List<RoomMember> left = roomMemberRepository.findLeftMembers(roomId);
        if (left.isEmpty()) {
            return;
        }
        for (RoomMember m : left) {
            m.setLeftAt(null);
            roomMemberRepository.save(m);
        }
    }

    /** End every live call this user still participates in. */
    private void supersedeLiveCallsForUser(UUID userId) {
        List<CallSession> live = callSessionRepository.findLiveCallsForUser(userId, LIVE);
        for (CallSession call : live) {
            supersedeCall(call);
        }
    }

    /**
     * Force-end a live call (ringing/active). Notifies peers; no chat line (caller may add one).
     *
     * @return true if status was updated
     */
    private boolean supersedeCall(CallSession call) {
        Instant now = Instant.now();
        CallStatus from = call.getStatus();
        if (from != CallStatus.ringing && from != CallStatus.active) {
            return false;
        }
        CallStatus to = from == CallStatus.ringing ? CallStatus.missed : CallStatus.ended;
        int updated = callSessionRepository.endIfStatus(call.getId(), from, to, now);
        if (updated == 0) {
            return false;
        }
        for (UUID participantId : callParticipantRepository.findUserIdsByCallId(call.getId())) {
            markLeft(call.getId(), participantId, now);
        }
        CallSession fresh = callSessionRepository.findByIdWithRoomAndInitiator(call.getId())
                .orElse(call);
        CallDto dto = toDto(fresh, resolveCallee(fresh));
        List<UUID> recipients = new ArrayList<>(
                callParticipantRepository.findUserIdsByCallId(fresh.getId()));
        afterCommit(() -> callEventPublisher.callEnded(dto, recipients));
        return true;
    }

    private void postCallEventMessage(CallSession call, String text) {
        CallSession session = callSessionRepository.findByIdWithRoomAndInitiator(call.getId())
                .orElse(call);
        User initiator = session.getInitiator();
        ChatMessage message = new ChatMessage();
        message.setRoom(session.getRoom());
        message.setSender(initiator);
        message.setType(MessageType.call_event);
        message.setContent(text);
        chatMessageRepository.saveAndFlush(message);

        ChatMessage saved = chatMessageRepository.findActiveById(message.getId()).orElse(message);
        List<UUID> memberIds = roomMemberRepository.findActiveMemberUserIds(session.getRoom().getId());
        UUID initiatorId = initiator.getId();
        UUID dtoViewer = memberIds.stream()
                .filter(id -> !id.equals(initiatorId))
                .findFirst()
                .orElse(initiatorId);
        MessageDto dto = ChatMapper.toMessageDto(saved, dtoViewer, null, true);
        UUID roomId = session.getRoom().getId();
        afterCommit(() -> messageEventPublisher.messageCreated(roomId, initiatorId, memberIds, dto));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private CallSession requireParticipantCall(UUID callId, UUID userId) {
        CallSession call = callSessionRepository.findByIdWithRoomAndInitiator(callId)
                .orElseThrow(() -> ApiException.notFound("Call not found"));
        if (!callParticipantRepository.existsByCallIdAndUserId(callId, userId)) {
            throw ApiException.forbidden("Not a call participant");
        }
        return call;
    }

    private CallSession requireActiveOrRinging(UUID callId, UUID userId) {
        CallSession call = requireParticipantCall(callId, userId);
        if (call.getStatus() != CallStatus.active && call.getStatus() != CallStatus.ringing) {
            throw ApiException.badRequest("call_state", "Call is not active");
        }
        return call;
    }

    private RoomMember requireMembership(UUID roomId, UUID userId) {
        return roomMemberRepository.findActiveMembership(roomId, userId)
                .orElseThrow(() -> ApiException.forbidden("Not a room member"));
    }

    private void saveParticipant(UUID callId, UUID userId, Instant joinedAt) {
        CallParticipant participant = new CallParticipant();
        participant.setCallId(callId);
        participant.setUserId(userId);
        participant.setJoinedAt(joinedAt);
        callParticipantRepository.save(participant);
    }

    private void markLeft(UUID callId, UUID userId, Instant leftAt) {
        callParticipantRepository.findById(new CallParticipant.Pk(callId, userId)).ifPresent(p -> {
            if (p.getLeftAt() == null) {
                p.setLeftAt(leftAt);
                callParticipantRepository.save(p);
            }
        });
    }

    private List<UUID> otherParticipants(UUID callId, UUID userId) {
        return callParticipantRepository.findUserIdsByCallId(callId).stream()
                .filter(id -> !id.equals(userId))
                .toList();
    }

    private User resolveCallee(CallSession call) {
        UUID initiatorId = call.getInitiator().getId();
        return callParticipantRepository.findUserIdsByCallId(call.getId()).stream()
                .filter(id -> !id.equals(initiatorId))
                .findFirst()
                .flatMap(userRepository::findById)
                .orElse(null);
    }

    private static CallMediaType parseCallType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("validation_error", "call_type is required");
        }
        try {
            return CallMediaType.valueOf(raw.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("validation_error", "call_type must be voice or video");
        }
    }

    private static void requireSdp(String sdp) {
        if (sdp == null || sdp.isBlank()) {
            throw ApiException.badRequest("validation_error", "sdp is required");
        }
    }

    private static String formatEndedSummary(CallSession call) {
        String kind = call.getCallType() == CallMediaType.video ? "Cuộc gọi video" : "Cuộc gọi thoại";
        if (call.getStartedAt() == null || call.getEndedAt() == null) {
            return kind;
        }
        long seconds = Math.max(0, Duration.between(call.getStartedAt(), call.getEndedAt()).getSeconds());
        long minutes = seconds / 60;
        long rem = seconds % 60;
        return kind + " · " + minutes + ":" + String.format(Locale.ROOT, "%02d", rem);
    }

    private CallDto toDto(CallSession call, User callee) {
        User initiator = call.getInitiator();
        return new CallDto(
                call.getId().toString(),
                call.getRoom().getId().toString(),
                initiator.getId().toString(),
                initiator.getDisplayName(),
                callee != null ? callee.getId().toString() : null,
                callee != null ? callee.getDisplayName() : null,
                call.getCallType().name(),
                call.getStatus().name(),
                formatInstant(call.getCreatedAt()),
                formatInstant(call.getStartedAt()),
                formatInstant(call.getEndedAt())
        );
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private void sendCallIncomingPush(CallSession call, UUID calleeId) {
        try {
            if (!fcmPushService.isEnabled()) {
                return;
            }
            List<UserDevice> devices = userDeviceRepository.findByUser_IdOrderByLastActiveAtDesc(calleeId);
            String callerName = call.getInitiator().getDisplayName();
            String callerId = call.getInitiator().getId().toString();
            String callId = call.getId().toString();
            String roomId = call.getRoom().getId().toString();
            String callType = call.getCallType().name();
            for (UserDevice device : devices) {
                String token = device.getFcmToken();
                if (token == null || token.isBlank() || token.startsWith("dev:")) {
                    continue;
                }
                fcmPushService.sendCallIncoming(token, callId, roomId, callerName, callerId, callType);
            }
        } catch (Exception ex) {
            log.warn("Failed to send call incoming push for call {}", call.getId(), ex);
        }
    }

    private void sendCallEndedPush(UUID callId, UUID targetUserId, String reason) {
        try {
            if (!fcmPushService.isEnabled() || targetUserId == null) {
                return;
            }
            List<UserDevice> devices = userDeviceRepository.findByUser_IdOrderByLastActiveAtDesc(targetUserId);
            for (UserDevice device : devices) {
                String token = device.getFcmToken();
                if (token == null || token.isBlank() || token.startsWith("dev:")) {
                    continue;
                }
                fcmPushService.sendCallEnded(token, callId.toString(), reason);
            }
        } catch (Exception ex) {
            log.warn("Failed to send call ended push for call {}", callId, ex);
        }
    }
}
