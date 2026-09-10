package com.kchat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kchat.common.dto.user.UpdateUserSettingsRequest;
import com.kchat.common.exception.ApiException;
import com.kchat.entity.User;
import com.kchat.entity.UserSettings;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserServiceImplQuietHoursTest {

  private UserSettings settings;

  @BeforeEach
  void setUp() {
    User user = new User();
    settings = UserSettings.defaultsFor(user);
    settings.setQuietHoursStart(LocalTime.of(22, 0));
    settings.setQuietHoursEnd(LocalTime.of(7, 0));
  }

  @Test
  void disableClearsHoursEvenWhenHoursSentInRequest() {
    UserServiceImpl.applyQuietHoursSettings(
        settings,
        new UpdateUserSettingsRequest(
            null, null, null, null, null, null, false, 22, 7, null, null));

    assertNull(settings.getQuietHoursStart());
    assertNull(settings.getQuietHoursEnd());
  }

  @Test
  void enableWithMissingHoursDefaultsToNightWindow() {
    settings.setQuietHoursStart(null);
    settings.setQuietHoursEnd(null);

    UserServiceImpl.applyQuietHoursSettings(
        settings,
        new UpdateUserSettingsRequest(
            null, null, null, null, null, null, true, null, null, null, null));

    assertEquals(LocalTime.of(22, 0), settings.getQuietHoursStart());
    assertEquals(LocalTime.of(7, 0), settings.getQuietHoursEnd());
  }

  @Test
  void updateHoursWhenEnabled() {
    UserServiceImpl.applyQuietHoursSettings(
        settings,
        new UpdateUserSettingsRequest(null, null, null, null, null, null, true, 21, 6, null, null));

    assertEquals(LocalTime.of(21, 0), settings.getQuietHoursStart());
    assertEquals(LocalTime.of(6, 0), settings.getQuietHoursEnd());
  }

  @Test
  void hourOnlyPatchWhileDisabledIsIgnored() {
    settings.setQuietHoursStart(null);
    settings.setQuietHoursEnd(null);

    UserServiceImpl.applyQuietHoursSettings(
        settings,
        new UpdateUserSettingsRequest(null, null, null, null, null, null, null, 21, 6, null, null));

    assertNull(settings.getQuietHoursStart());
    assertNull(settings.getQuietHoursEnd());
  }

  @Test
  void rejectEqualStartAndEnd() {
    assertThrows(
        ApiException.class,
        () ->
            UserServiceImpl.applyQuietHoursSettings(
                settings,
                new UpdateUserSettingsRequest(
                    null, null, null, null, null, null, true, 22, 22, null, null)));
  }
}
