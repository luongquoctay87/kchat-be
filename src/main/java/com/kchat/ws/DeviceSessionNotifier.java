package com.kchat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kchat.common.dto.ws.WsEnvelope;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DeviceSessionNotifier {

  private static final Logger log = LoggerFactory.getLogger(DeviceSessionNotifier.class);

  private final WsSessionRegistry sessionRegistry;
  private final ObjectMapper objectMapper;

  public DeviceSessionNotifier(WsSessionRegistry sessionRegistry, ObjectMapper objectMapper) {
    this.sessionRegistry = sessionRegistry;
    this.objectMapper = objectMapper;
  }

  public void notifyDeviceRevoked(UUID userId, String deviceToken) {
    try {
      WsEnvelope envelope =
          WsEnvelope.deviceSessionRevoked(new WsEnvelope.DeviceSessionRevokedPayload(deviceToken));
      sessionRegistry.sendToUser(userId, objectMapper.writeValueAsString(envelope));
    } catch (Exception ex) {
      log.warn("Failed to notify device revoke for user {}: {}", userId, ex.getMessage());
    }
  }
}
