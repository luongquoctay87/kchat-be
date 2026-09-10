package com.kchat.repository;

import java.util.UUID;

/** Native query projection for disappearing-message cleanup (#20). */
public interface ExpiredMessageRow {

  UUID getId();

  UUID getRoomId();
}
