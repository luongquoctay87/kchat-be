package com.kchat.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class DeviceSessionFilterTest {

  @Test
  void postDevices_isRegistrationRequest() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    org.mockito.Mockito.when(request.getMethod()).thenReturn(HttpMethod.POST.name());
    org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/devices");

    assertTrue(DeviceSessionFilter.isDeviceRegistrationRequest(request));
  }

  @Test
  void deleteDevice_isNotRegistrationRequest() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    org.mockito.Mockito.when(request.getMethod()).thenReturn(HttpMethod.DELETE.name());
    org.mockito.Mockito.when(request.getRequestURI())
        .thenReturn("/devices/550e8400-e29b-41d4-a716-446655440000");

    assertFalse(DeviceSessionFilter.isDeviceRegistrationRequest(request));
  }
}
