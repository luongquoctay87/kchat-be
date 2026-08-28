package com.kchat.service;

import com.kchat.common.dto.user.DeviceDto;
import com.kchat.common.dto.user.UpdateProfileRequest;
import com.kchat.common.dto.user.UpdateUserSettingsRequest;
import com.kchat.common.dto.user.UserProfileDto;
import com.kchat.common.dto.user.UserSettingsDto;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    UserProfileDto getProfile(UUID userId);

    UserProfileDto updateProfile(UUID userId, UpdateProfileRequest request);

    UserProfileDto updateAvatar(UUID userId, MultipartFile file);

    /** S3 object key for an existing avatar. */
    String requireAvatarKey(UUID userId);

    UserSettingsDto getSettings(UUID userId);

    UserSettingsDto updateSettings(UUID userId, UpdateUserSettingsRequest request);

    List<DeviceDto> listDevices(UUID userId, String deviceToken);

    void revokeDevice(UUID userId, UUID deviceId, String deviceToken);
}
