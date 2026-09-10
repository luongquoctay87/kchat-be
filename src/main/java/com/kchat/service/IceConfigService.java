package com.kchat.service;

import com.kchat.common.dto.call.IceServersResponse;
import java.util.UUID;

public interface IceConfigService {

  /** ICE servers for the authenticated caller (may include ephemeral TURN creds). */
  IceServersResponse iceServersForUser(UUID userId);
}
