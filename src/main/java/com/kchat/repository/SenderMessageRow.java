package com.kchat.repository;

import java.util.UUID;

/** Native query projection for bulk sender message wipe. */
public interface SenderMessageRow {

  UUID getId();

  UUID getRoomId();
}
