package com.kchat.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.kchat.config.FcmProperties;
import com.kchat.repository.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FcmPushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);

    private final FcmProperties properties;
    private final UserDeviceRepository userDeviceRepository;

    public FcmPushService(FcmProperties properties, UserDeviceRepository userDeviceRepository) {
        this.properties = properties;
        this.userDeviceRepository = userDeviceRepository;
    }

    public boolean isEnabled() {
        return properties.isConfigured() && !FirebaseApp.getApps().isEmpty();
    }

    public void sendRoomMessage(
            String fcmToken,
            String roomId,
            String roomTitle,
            String senderName,
            String body,
            String messageId,
            Long createdAt,
            String messageType
    ) {
        if (!isEnabled()) {
            return;
        }
        String title = roomTitle == null || roomTitle.isBlank() ? "k-chat" : roomTitle;
        String preview = body == null || body.isBlank() ? "Tin nhắn mới" : body;
        String line = senderName == null || senderName.isBlank() ? preview : senderName + ": " + preview;

        java.util.HashMap<String, String> data = new java.util.HashMap<>();
        data.put("type", "message_new");
        data.put("room_id", roomId);
        data.put("room_title", title);
        data.put("sender_name", senderName == null ? "" : senderName);
        data.put("body", preview);
        if (messageId != null && !messageId.isBlank()) {
            data.put("message_id", messageId);
        }
        if (createdAt != null) {
            data.put("created_at", Long.toString(createdAt));
        }
        if (messageType != null && !messageType.isBlank()) {
            data.put("message_type", messageType);
        }

        // Notification + data: Android still shows a tray item when the app is killed.
        // Foreground delivery still hits onMessageReceived for in-room suppression.
        Message message = Message.builder()
                .setToken(fcmToken)
                .putAllData(data)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(line)
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId("kchat_messages")
                                .setSound("default")
                                .setTag(roomId)
                                .build())
                        .build())
                .build();
        try {
            String fcmMessageId = FirebaseMessaging.getInstance().send(message);
            log.debug("FCM sent messageId={} room={}", fcmMessageId, roomId);
        } catch (FirebaseMessagingException ex) {
            if (isStaleToken(ex)) {
                log.info("Removing stale FCM token");
                userDeviceRepository.deleteByFcmToken(fcmToken);
            } else {
                log.warn("FCM send failed: {}", ex.getMessagingErrorCode(), ex);
            }
        }
    }

    private static boolean isStaleToken(FirebaseMessagingException ex) {
        MessagingErrorCode code = ex.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.INVALID_ARGUMENT
                || code == MessagingErrorCode.SENDER_ID_MISMATCH;
    }
}
