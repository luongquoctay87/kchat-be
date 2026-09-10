package com.kchat.service.impl;

import com.kchat.common.dto.user.DeviceDto;
import com.kchat.common.dto.user.UpdateProfileRequest;
import com.kchat.common.dto.user.UpdateUserSettingsRequest;
import com.kchat.common.dto.user.UserProfileDto;
import com.kchat.common.dto.user.UserSettingsDto;
import com.kchat.common.exception.ApiException;
import com.kchat.common.util.ChatTimeFormat;
import com.kchat.common.util.MessageStorageOptions;
import com.kchat.entity.User;
import com.kchat.entity.UserDevice;
import com.kchat.entity.UserSettings;
import com.kchat.media.AvatarRules;
import com.kchat.media.MediaKeys;
import com.kchat.media.MediaStorage;
import com.kchat.repository.RefreshTokenRepository;
import com.kchat.repository.UserDeviceRepository;
import com.kchat.repository.UserRepository;
import com.kchat.repository.UserSettingsRepository;
import com.kchat.service.UserService;
import com.kchat.ws.DeviceSessionNotifier;
import com.kchat.ws.PresenceEventPublisher;
import com.kchat.ws.PresenceService;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class UserServiceImpl implements UserService {

  private static final List<String> THEMES = List.of("system", "light", "dark");
  private static final List<String> FONT_SIZES = List.of("small", "medium", "large");
  private static final List<String> PRIVACY_DM = List.of("everyone", "contacts", "none");

  private final UserRepository userRepository;
  private final UserSettingsRepository userSettingsRepository;
  private final UserDeviceRepository userDeviceRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PresenceService presenceService;
  private final PresenceEventPublisher presenceEventPublisher;
  private final DeviceSessionNotifier deviceSessionNotifier;
  private final MediaStorage mediaStorage;

  public UserServiceImpl(
      UserRepository userRepository,
      UserSettingsRepository userSettingsRepository,
      UserDeviceRepository userDeviceRepository,
      RefreshTokenRepository refreshTokenRepository,
      PresenceService presenceService,
      PresenceEventPublisher presenceEventPublisher,
      DeviceSessionNotifier deviceSessionNotifier,
      MediaStorage mediaStorage) {
    this.userRepository = userRepository;
    this.userSettingsRepository = userSettingsRepository;
    this.userDeviceRepository = userDeviceRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.presenceService = presenceService;
    this.presenceEventPublisher = presenceEventPublisher;
    this.deviceSessionNotifier = deviceSessionNotifier;
    this.mediaStorage = mediaStorage;
  }

  @Override
  @Transactional(readOnly = true)
  public UserProfileDto getProfile(UUID userId) {
    return toProfile(requireUser(userId));
  }

  @Override
  public UserProfileDto updateProfile(UUID userId, UpdateProfileRequest request) {
    User user = requireUser(userId);
    user.setDisplayName(request.displayName().trim());
    String phone = request.phone() == null ? "" : request.phone().trim();
    user.setPhone(phone.isBlank() ? null : phone);
    return toProfile(user);
  }

  @Override
  public UserProfileDto updateAvatar(UUID userId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw ApiException.badRequest("validation_error", "file is required");
    }
    if (file.getSize() > AvatarRules.MAX_BYTES) {
      throw ApiException.badRequest("validation_error", "Avatar must be at most 5MB");
    }
    if (!AvatarRules.isAllowedMime(file.getContentType())) {
      throw ApiException.badRequest("validation_error", "Avatar must be JPEG, PNG, WebP, or GIF");
    }

    User user = requireUser(userId);
    String previousKey = user.getAvatarUrl();
    MediaStorage.StoredObject stored;
    try {
      stored = mediaStorage.storeAvatar(userId, file);
    } catch (IOException ex) {
      throw ApiException.badRequest("upload_failed", "Could not store avatar");
    }
    user.setAvatarUrl(stored.key());
    userRepository.saveAndFlush(user);

    if (previousKey != null && !previousKey.equals(stored.key())) {
      runAfterCommit(() -> mediaStorage.deleteQuietly(previousKey));
    }
    return toProfile(user);
  }

  @Override
  @Transactional(readOnly = true)
  public String requireAvatarKey(UUID userId) {
    User user = requireUser(userId);
    String key = user.getAvatarUrl();
    if (!MediaKeys.isAvatarKey(userId, key)) {
      throw ApiException.notFound("Avatar not found");
    }
    return key;
  }

  @Override
  @Transactional(readOnly = true)
  public UserSettingsDto getSettings(UUID userId) {
    return toSettings(requireSettings(userId));
  }

  @Override
  public UserSettingsDto updateSettings(UUID userId, UpdateUserSettingsRequest request) {
    UserSettings settings = requireSettings(userId);

    if (request.pushEnabled() != null) {
      settings.setPushEnabled(request.pushEnabled());
    }
    if (request.theme() != null) {
      String theme = request.theme().trim().toLowerCase(Locale.ROOT);
      if (!THEMES.contains(theme)) {
        throw ApiException.badRequest("validation_error", "Invalid theme");
      }
      settings.setTheme(theme);
    }
    if (request.fontSize() != null) {
      String fontSize = request.fontSize().trim().toLowerCase(Locale.ROOT);
      if (!FONT_SIZES.contains(fontSize)) {
        throw ApiException.badRequest("validation_error", "Invalid font size");
      }
      settings.setFontSize(fontSize);
    }
    if (request.showOnline() != null) {
      boolean wasVisible = settings.isShowOnline();
      boolean nowVisible = request.showOnline();
      settings.setShowOnline(nowVisible);
      if (wasVisible != nowVisible && presenceService.isOnline(userId)) {
        presenceEventPublisher.presenceChanged(userId, nowVisible);
      }
    }
    if (request.enterToSend() != null) {
      settings.setEnterToSend(request.enterToSend());
    }
    if (request.privacyDm() != null) {
      String privacy = request.privacyDm().trim().toLowerCase(Locale.ROOT);
      if (!PRIVACY_DM.contains(privacy)) {
        throw ApiException.badRequest("validation_error", "Invalid privacy setting");
      }
      settings.setPrivacyDm(privacy);
    }
    applyQuietHoursSettings(settings, request);
    applyMessageStorageSettings(settings, request);

    userSettingsRepository.save(settings);
    return toSettings(settings);
  }

  /** Quiet hours are enabled iff both start and end are set. Package-visible for unit tests. */
  static void applyQuietHoursSettings(UserSettings settings, UpdateUserSettingsRequest request) {
    if (Boolean.FALSE.equals(request.quietHoursEnabled())) {
      settings.setQuietHoursStart(null);
      settings.setQuietHoursEnd(null);
      return;
    }

    boolean enabling = Boolean.TRUE.equals(request.quietHoursEnabled());
    boolean alreadyOn =
        settings.getQuietHoursStart() != null && settings.getQuietHoursEnd() != null;
    boolean updatingHours =
        request.quietHoursStartHour() != null || request.quietHoursEndHour() != null;

    // Ignore stray hour fields while quiet hours are off and not being turned on.
    if (!enabling && !alreadyOn) {
      return;
    }
    if (!enabling && !updatingHours) {
      return;
    }

    LocalTime start =
        request.quietHoursStartHour() != null
            ? hourToTime(request.quietHoursStartHour())
            : settings.getQuietHoursStart();
    LocalTime end =
        request.quietHoursEndHour() != null
            ? hourToTime(request.quietHoursEndHour())
            : settings.getQuietHoursEnd();
    if (start == null) {
      start = LocalTime.of(22, 0);
    }
    if (end == null) {
      end = LocalTime.of(7, 0);
    }
    if (start.equals(end)) {
      throw ApiException.badRequest("validation_error", "Quiet hours start and end must differ");
    }
    settings.setQuietHoursStart(start);
    settings.setQuietHoursEnd(end);
  }

  static void applyMessageStorageSettings(
      UserSettings settings, UpdateUserSettingsRequest request) {
    if (request.localCacheRetentionDays() != null) {
      if (!MessageStorageOptions.isValidCacheRetention(request.localCacheRetentionDays())) {
        throw ApiException.badRequest("validation_error", "Invalid cache retention days");
      }
      settings.setLocalCacheRetentionDays(request.localCacheRetentionDays());
    }
    if (request.defaultDisappearingSeconds() != null) {
      if (!MessageStorageOptions.isValidDefaultDisappearing(request.defaultDisappearingSeconds())) {
        throw ApiException.badRequest("validation_error", "Invalid default disappearing duration");
      }
      if (request.defaultDisappearingSeconds()
          == MessageStorageOptions.CLEAR_DEFAULT_DISAPPEARING) {
        settings.setDefaultDisappearingSeconds(null);
      } else {
        settings.setDefaultDisappearingSeconds(request.defaultDisappearingSeconds());
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<DeviceDto> listDevices(UUID userId, String deviceToken) {
    List<UserDevice> devices = userDeviceRepository.findByUser_IdOrderByLastActiveAtDesc(userId);
    String normalizedToken = normalizeDeviceToken(deviceToken);
    return devices.stream()
        .map(
            device ->
                toDevice(
                    device,
                    normalizedToken != null && normalizedToken.equals(device.getFcmToken())))
        .toList();
  }

  @Override
  public void revokeDevice(UUID userId, UUID deviceId, String deviceToken) {
    UserDevice device =
        userDeviceRepository
            .findById(deviceId)
            .orElseThrow(() -> ApiException.notFound("Device not found"));
    if (!device.getUser().getId().equals(userId)) {
      throw ApiException.forbidden("Not your device");
    }
    String normalizedToken = normalizeDeviceToken(deviceToken);
    if (normalizedToken != null && normalizedToken.equals(device.getFcmToken())) {
      throw ApiException.badRequest("validation_error", "Cannot revoke the current device");
    }
    String revokedToken = device.getFcmToken();
    refreshTokenRepository.revokeActiveForUserAndDevice(userId, revokedToken);
    userDeviceRepository.delete(device);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            deviceSessionNotifier.notifyDeviceRevoked(userId, revokedToken);
          }
        });
  }

  private static String normalizeDeviceToken(String deviceToken) {
    if (deviceToken == null || deviceToken.isBlank()) {
      return null;
    }
    return deviceToken.trim();
  }

  private User requireUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> ApiException.unauthorized("User not found"));
  }

  private UserSettings requireSettings(UUID userId) {
    return userSettingsRepository
        .findById(userId)
        .orElseGet(
            () -> {
              User user = requireUser(userId);
              return userSettingsRepository.save(UserSettings.defaultsFor(user));
            });
  }

  private UserProfileDto toProfile(User user) {
    return new UserProfileDto(
        user.getId().toString(),
        user.getUsername(),
        user.getEmail() != null ? user.getEmail() : "",
        user.getDisplayName(),
        user.getPhone() != null ? user.getPhone() : "",
        publicAvatarUrl(user));
  }

  /** Relative URL clients resolve against API base; includes cache-busting version. */
  static String publicAvatarUrl(User user) {
    String key = user.getAvatarUrl();
    if (user.getId() == null || !MediaKeys.isAvatarKey(user.getId(), key)) {
      return null;
    }
    String version = key.substring(key.lastIndexOf('/') + 1);
    String encoded = URLEncoder.encode(version, StandardCharsets.UTF_8);
    return "/users/" + user.getId() + "/avatar?v=" + encoded;
  }

  private void runAfterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }

  private UserSettingsDto toSettings(UserSettings settings) {
    boolean quietEnabled =
        settings.getQuietHoursStart() != null && settings.getQuietHoursEnd() != null;
    return new UserSettingsDto(
        settings.isPushEnabled(),
        settings.getTheme(),
        settings.getFontSize(),
        settings.isShowOnline(),
        settings.isEnterToSend(),
        settings.getPrivacyDm(),
        quietEnabled,
        quietEnabled ? settings.getQuietHoursStart().getHour() : null,
        quietEnabled ? settings.getQuietHoursEnd().getHour() : null,
        settings.getLocalCacheRetentionDays(),
        settings.getDefaultDisappearingSeconds());
  }

  private DeviceDto toDevice(UserDevice device, boolean isCurrent) {
    String platform = device.getPlatform() != null ? device.getPlatform() : "android";
    String subtitle = formatDeviceSubtitle(platform, device.getLastActiveAt(), isCurrent);
    String name =
        device.getDeviceName() != null && !device.getDeviceName().isBlank()
            ? device.getDeviceName()
            : platform;
    return new DeviceDto(device.getId().toString(), name, subtitle, isCurrent);
  }

  private static String formatDeviceSubtitle(
      String platform, Instant lastActiveAt, boolean isCurrent) {
    if (isCurrent) {
      return capitalize(platform) + " · Đang dùng";
    }
    if (lastActiveAt == null) {
      return capitalize(platform);
    }
    return capitalize(platform) + " · " + ChatTimeFormat.formatLastSeen(lastActiveAt);
  }

  private static String capitalize(String value) {
    if (value == null || value.isBlank()) {
      return "Thiết bị";
    }
    return value.substring(0, 1).toUpperCase(Locale.ROOT)
        + value.substring(1).toLowerCase(Locale.ROOT);
  }

  private static LocalTime hourToTime(int hour) {
    if (hour < 0 || hour > 23) {
      throw ApiException.badRequest("validation_error", "Hour must be between 0 and 23");
    }
    return LocalTime.of(hour, 0);
  }
}
