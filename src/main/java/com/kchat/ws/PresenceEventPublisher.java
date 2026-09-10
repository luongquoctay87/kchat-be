package com.kchat.ws;

import java.util.UUID;

public interface PresenceEventPublisher {

  void presenceChanged(UUID userId, boolean online);
}
