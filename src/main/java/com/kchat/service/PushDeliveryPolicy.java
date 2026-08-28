package com.kchat.service;

import com.kchat.entity.RoomMember;
import com.kchat.entity.UserDevice;
import com.kchat.entity.UserSettings;
import com.kchat.repository.UserDeviceRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PushDeliveryPolicy {

    private final UserDeviceRepository userDeviceRepository;

    public PushDeliveryPolicy(UserDeviceRepository userDeviceRepository) {
        this.userDeviceRepository = userDeviceRepository;
    }

    public boolean shouldSendRoomMessagePush(UserSettings settings, RoomMember membership, Instant now) {
        return shouldSendRoomMessagePush(settings, membership, now, ZoneOffset.UTC);
    }

    public boolean shouldSendRoomMessagePush(
            UserSettings settings,
            RoomMember membership,
            Instant now,
            UUID recipientUserId
    ) {
        return shouldSendRoomMessagePush(settings, membership, now, resolveZone(recipientUserId));
    }

    public boolean shouldSendRoomMessagePush(
            UserSettings settings,
            RoomMember membership,
            Instant now,
            ZoneId zone
    ) {
        if (settings != null && !settings.shouldReceivePush(now.atZone(zone).toLocalTime())) {
            return false;
        }
        return !com.kchat.common.util.RoomMuteOptions.isMuted(membership.getMutedUntil(), now);
    }

    private ZoneId resolveZone(UUID userId) {
        List<UserDevice> devices = userDeviceRepository.findByUser_IdOrderByLastActiveAtDesc(userId);
        for (UserDevice device : devices) {
            Integer offsetMinutes = device.getUtcOffsetMinutes();
            if (offsetMinutes != null) {
                return ZoneOffset.ofTotalSeconds(offsetMinutes * 60);
            }
        }
        return ZoneOffset.UTC;
    }
}
