package com.kchat.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.kchat.config.FcmProperties;
import com.kchat.repository.UserDeviceRepository;
import java.util.Map;
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
            String body
    ) {
        if (!isEnabled()) {
            return;
        }
        String title = roomTitle == null || roomTitle.isBlank() ? "k-chat" : roomTitle;
        String preview = body == null || body.isBlank() ? "Tin nhắn mới" : body;

        // Data-only so Android always invokes onMessageReceived (active-room suppression, channel).
        Message message = Message.builder()
                .setToken(fcmToken)
                .putAllData(Map.of(
                        "type", "message_new",
                        "room_id", roomId,
                        "room_title", title,
                        "sender_name", senderName == null ? "" : senderName,
                        "body", preview
                ))
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                .build();
        try {
            FirebaseMessaging.getInstance().send(message);
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
