package com.kchat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kchat.common.dto.user.UpdateUserSettingsRequest;
import com.kchat.common.exception.ApiException;
import com.kchat.common.util.MessageStorageOptions;
import com.kchat.entity.User;
import com.kchat.entity.UserSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserServiceImplMessageStorageTest {

  private UserSettings settings;

  @BeforeEach
  void setUp() {
    settings = UserSettings.defaultsFor(new User());
  }

  @Test
  void setCacheRetentionDays() {
    UserServiceImpl.applyMessageStorageSettings(
        settings,
        new UpdateUserSettingsRequest(
            null, null, null, null, null, null, null, null, null, 30, null));
    assertEquals(30, settings.getLocalCacheRetentionDays());
  }

  @Test
  void rejectInvalidCacheRetention() {
    assertThrows(
        ApiException.class,
        () ->
            UserServiceImpl.applyMessageStorageSettings(
                settings,
                new UpdateUserSettingsRequest(
                    null, null, null, null, null, null, null, null, null, 14, null)));
  }

  @Test
  void setDefaultDisappearing() {
    UserServiceImpl.applyMessageStorageSettings(
        settings,
        new UpdateUserSettingsRequest(
            null, null, null, null, null, null, null, null, null, null, 604_800));
    assertEquals(604_800, settings.getDefaultDisappearingSeconds());
  }

  @Test
  void clearDefaultDisappearingWithZero() {
    settings.setDefaultDisappearingSeconds(86_400);
    UserServiceImpl.applyMessageStorageSettings(
        settings,
        new UpdateUserSettingsRequest(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            MessageStorageOptions.CLEAR_DEFAULT_DISAPPEARING));
    assertNull(settings.getDefaultDisappearingSeconds());
  }
}
