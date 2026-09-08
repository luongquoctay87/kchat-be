package com.kchat.service;

import com.kchat.common.dto.chat.MessageDto;
import com.kchat.common.enums.RoomType;
import com.kchat.common.util.ChannelRules;
import com.kchat.entity.ChatRoom;
import com.kchat.entity.DirectRoomPair;
import com.kchat.entity.RoomMember;
import com.kchat.entity.User;
import com.kchat.entity.UserDevice;
import com.kchat.entity.UserSettings;
import com.kchat.repository.ChatRoomRepository;
import com.kchat.repository.DirectRoomPairRepository;
import com.kchat.repository.RoomMemberRepository;
import com.kchat.repository.UserDeviceRepository;
import com.kchat.repository.UserSettingsRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessagePushDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(MessagePushDeliveryService.class);

    private final FcmPushService fcmPushService;
    private final PushDeliveryPolicy pushDeliveryPolicy;
    private final UserSettingsRepository userSettingsRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final DirectRoomPairRepository directRoomPairRepository;

    public MessagePushDeliveryService(
            FcmPushService fcmPushService,
            PushDeliveryPolicy pushDeliveryPolicy,
            UserSettingsRepository userSettingsRepository,
            RoomMemberRepository roomMemberRepository,
            UserDeviceRepository userDeviceRepository,
            ChatRoomRepository chatRoomRepository,
            DirectRoomPairRepository directRoomPairRepository
    ) {
        this.fcmPushService = fcmPushService;
        this.pushDeliveryPolicy = pushDeliveryPolicy;
        this.userSettingsRepository = userSettingsRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.directRoomPairRepository = directRoomPairRepository;
    }

    @Transactional(readOnly = true)
    public void deliver(UUID roomId, UUID senderId, List<UUID> memberIds, MessageDto message) {
        if (!fcmPushService.isEnabled()) {
            log.debug("FCM push is disabled globally, skipping deliver for room {}", roomId);
            return;
        }
        if (memberIds == null || memberIds.isEmpty()) {
            return;
        }
        try {
            Instant now = Instant.now();
            ChatRoom room = chatRoomRepository.findById(roomId).orElse(null);
            if (room == null) {
                return;
            }
            DirectRoomPair pair = room.getType() == RoomType.direct
                    ? directRoomPairRepository.findByRoomIdIn(List.of(roomId)).stream().findFirst().orElse(null)
                    : null;
            String body = formatBody(message);
            String senderName = message.senderName() == null ? "" : message.senderName();

            for (UUID memberId : memberIds) {
                if (senderId != null && memberId.equals(senderId)) {
                    continue;
                }
                // Per-device suppression is handled on Android (active room). Skip only when
                // user is globally offline from push perspective is NOT used — deliver to all
                // registered FCM tokens so multi-device works (phone background + tablet online).
                RoomMember membership = roomMemberRepository.findActiveMembership(roomId, memberId).orElse(null);
                if (membership == null) {
                    continue;
                }
                UserSettings settings = userSettingsRepository.findById(memberId).orElse(null);
                if (!pushDeliveryPolicy.shouldSendRoomMessagePush(settings, membership, now, memberId)) {
                    log.info("Push skipped for member {}: pushEnabled={}, quietHours/mute active",
                            memberId, settings != null && settings.isPushEnabled());
                    continue;
                }
                String title = resolveTitle(room, pair, memberId);
                List<UserDevice> devices = userDeviceRepository.findByUser_IdOrderByLastActiveAtDesc(memberId);
                int sentCount = 0;
                for (UserDevice device : devices) {
                    String token = device.getFcmToken();
                    if (token == null || token.isBlank() || token.startsWith("dev:")) {
                        continue;
                    }
                    sentCount++;
                    fcmPushService.sendRoomMessage(
                            token,
                            roomId.toString(),
                            title,
                            senderName,
                            body,
                            message == null ? null : message.id(),
                            message == null ? null : message.createdAt(),
                            message == null ? null : message.type()
                    );
                }
                if (sentCount == 0) {
                    log.info("No valid FCM push tokens for recipient {} (total devices: {})", memberId, devices.size());
                }
            }
        } catch (Exception ex) {
            log.error("FCM delivery failed for room {}", roomId, ex);
        }
    }

    private static String formatBody(MessageDto message) {
        if (message == null) {
            return "Tin nhắn mới";
        }
        String type = message.type() == null ? "text" : message.type();
        return switch (type) {
            case "image" -> "[Ảnh]";
            case "file" -> message.fileName() == null || message.fileName().isBlank()
                    ? "[File]"
                    : "[File] " + message.fileName();
            case "call_event" -> {
                String text = message.text();
                if (text == null || text.isBlank()) {
                    yield "[Cuộc gọi]";
                }
                yield truncate(text, 120);
            }
            case "system" -> message.botTitle() == null || message.botTitle().isBlank()
                    ? "[Bot]"
                    : message.botTitle();
            default -> {
                String text = message.text();
                if (text == null || text.isBlank()) {
                    yield "Tin nhắn mới";
                }
                yield truncate(text, 120);
            }
        };
    }

    private String resolveTitle(ChatRoom room, DirectRoomPair pair, UUID userId) {
        if (room.getType() == RoomType.direct) {
            if (pair != null) {
                try {
                    return displayName(pair.otherUser(userId));
                } catch (RuntimeException ignored) {
                    // fall through
                }
            }
            return fallbackPeerTitle(room.getId(), userId);
        }
        if (room.getType() == RoomType.channel) {
            return ChannelRules.displayTitle(room.getName(), room.getSlug());
        }
        if (room.getName() != null && !room.getName().isBlank()) {
            return room.getName();
        }
        if (room.getSlug() != null && !room.getSlug().isBlank()) {
            return room.getSlug();
        }
        return fallbackPeerTitle(room.getId(), userId);
    }

    private String fallbackPeerTitle(UUID roomId, UUID userId) {
        List<User> others = roomMemberRepository.findOtherActiveMembers(roomId, userId).stream()
                .map(RoomMember::getUser)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (others.isEmpty()) {
            return "Chat";
        }
        List<User> shown = others.size() > 3 ? others.subList(0, 3) : others;
        return shown.stream().map(MessagePushDeliveryService::displayName).collect(java.util.stream.Collectors.joining(", "));
    }

    private static String displayName(User user) {
        if (user == null) {
            return "Thành viên";
        }
        String name = user.getDisplayName();
        if (name == null || name.isBlank()) {
            return user.loginName() != null ? user.loginName() : "Thành viên";
        }
        return name.trim();
    }

    private static String truncate(String value, int max) {
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max - 1) + "…";
    }
}
