package com.kchat.common.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
    @NotBlank @Size(max = 512) String fcmToken,
    @NotBlank @Size(max = 128) String deviceName,
    @Size(max = 20) String platform,
    /** Device UTC offset in minutes (e.g. +420 for UTC+7). Used for quiet-hours push gating. */
    Integer utcOffsetMinutes) {}
