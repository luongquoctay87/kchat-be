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
      String messageType) {
    if (!isEnabled()) {
      return;
    }
    String title = roomTitle == null || roomTitle.isBlank() ? "k-chat" : roomTitle;
    String preview = body == null || body.isBlank() ? "Tin nhắn mới" : body;
    String line =
        senderName == null || senderName.isBlank() ? preview : senderName + ": " + preview;

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
      data.put("msg_type", messageType);
    }

    // Notification + data: Android still shows a tray item when the app is killed.
    // Foreground delivery still hits onMessageReceived for in-room suppression.
    Message message =
        Message.builder()
            .setToken(fcmToken)
            .putAllData(data)
            .setNotification(Notification.builder().setTitle(title).setBody(line).build())
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(
                        AndroidNotification.builder()
                            .setChannelId("kchat_messages")
                            .setPriority(AndroidNotification.Priority.HIGH)
                            .setDefaultSound(true)
                            .setDefaultVibrateTimings(true)
                            .setSound("default")
                            .setTag(roomId)
                            .build())
                    .build())
            .build();
    try {
      String fcmMessageId = FirebaseMessaging.getInstance().send(message);
      log.info(
          "FCM sent messageId={} room={} tokenPrefix={}",
          fcmMessageId,
          roomId,
          fcmToken.substring(0, Math.min(10, fcmToken.length())));
    } catch (FirebaseMessagingException ex) {
      if (isStaleToken(ex)) {
        log.info(
            "Removing stale FCM token (errorCode={}, message={})",
            ex.getMessagingErrorCode(),
            ex.getMessage());
        userDeviceRepository.deleteByFcmToken(fcmToken);
      } else {
        log.warn("FCM send failed: {} - {}", ex.getMessagingErrorCode(), ex.getMessage());
      }
    }
  }

  public void sendCallIncoming(
      String fcmToken,
      String callId,
      String roomId,
      String callerName,
      String callerId,
      String callType) {
    if (!isEnabled()) {
      return;
    }
    String isVideoStr = "video".equalsIgnoreCase(callType) ? "video" : "thoại";
    String title = "Cuộc gọi " + isVideoStr + " đến";
    String preview =
        (callerName == null || callerName.isBlank() ? "k-chat" : callerName) + " đang gọi cho bạn";

    java.util.HashMap<String, String> data = new java.util.HashMap<>();
    data.put("type", "call_incoming");
    data.put("call_id", callId);
    data.put("room_id", roomId);
    data.put("caller_name", callerName == null ? "" : callerName);
    data.put("caller_id", callerId == null ? "" : callerId);
    data.put("call_type", callType == null ? "voice" : callType);

    Message message =
        Message.builder()
            .setToken(fcmToken)
            .putAllData(data)
            .setNotification(Notification.builder().setTitle(title).setBody(preview).build())
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setTtl(45_000L) // 45 seconds ring timeout
                    .setNotification(
                        AndroidNotification.builder()
                            .setChannelId("kchat_calls")
                            .setSound("default")
                            .setTag("call_" + callId)
                            .build())
                    .build())
            .build();
    try {
      String fcmMessageId = FirebaseMessaging.getInstance().send(message);
      log.info(
          "FCM sent call_incoming messageId={} callId={} tokenPrefix={}",
          fcmMessageId,
          callId,
          fcmToken.substring(0, Math.min(10, fcmToken.length())));
    } catch (FirebaseMessagingException ex) {
      if (isStaleToken(ex)) {
        log.info(
            "Removing stale FCM token (errorCode={}, message={})",
            ex.getMessagingErrorCode(),
            ex.getMessage());
        userDeviceRepository.deleteByFcmToken(fcmToken);
      } else {
        log.warn(
            "FCM call_incoming send failed tokenPrefix={}: {} - {}",
            fcmToken.substring(0, Math.min(10, fcmToken.length())),
            ex.getMessagingErrorCode(),
            ex.getMessage());
      }
    } catch (Exception ex) {
      log.error("FCM unexpected failure for call_incoming callId={}", callId, ex);
    }
  }

  public void sendCallEnded(String fcmToken, String callId, String reason) {
    if (!isEnabled()) {
      return;
    }
    java.util.HashMap<String, String> data = new java.util.HashMap<>();
    data.put("type", "call_ended");
    data.put("call_id", callId);
    data.put("reason", reason == null ? "ended" : reason);

    Message message =
        Message.builder()
            .setToken(fcmToken)
            .putAllData(data)
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setTtl(10_000L)
                    .build())
            .build();
    try {
      FirebaseMessaging.getInstance().send(message);
      log.info(
          "FCM sent call_ended callId={} reason={} tokenPrefix={}",
          callId,
          reason,
          fcmToken.substring(0, Math.min(10, fcmToken.length())));
    } catch (Exception ignored) {
    }
  }

  private static boolean isStaleToken(FirebaseMessagingException ex) {
    return ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED;
  }
}
