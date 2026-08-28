package com.kchat.service;

import com.kchat.common.dto.chat.MessageDto;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

@Component
public class MessagePushNotifier {

    private final FcmPushService fcmPushService;
    private final MessagePushDeliveryService deliveryService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public MessagePushNotifier(FcmPushService fcmPushService, MessagePushDeliveryService deliveryService) {
        this.fcmPushService = fcmPushService;
        this.deliveryService = deliveryService;
    }

    public void onMessageCreated(
            UUID roomId,
            UUID senderId,
            List<UUID> memberIds,
            MessageDto message
    ) {
        if (!fcmPushService.isEnabled() || memberIds == null || memberIds.isEmpty()) {
            return;
        }
        executor.execute(() -> deliveryService.deliver(roomId, senderId, memberIds, message));
    }
}
