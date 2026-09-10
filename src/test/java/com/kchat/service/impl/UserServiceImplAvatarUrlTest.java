package com.kchat.service.impl;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kchat.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserServiceImplAvatarUrlTest {

  @Test
  void publicAvatarUrl_nullWhenMissing() {
    User user = new User();
    user.setId(UUID.randomUUID());
    assertNull(UserServiceImpl.publicAvatarUrl(user));
  }

  @Test
  void publicAvatarUrl_nullWhenKeyNotOwned() {
    UUID id = UUID.randomUUID();
    User user = new User();
    user.setId(id);
    user.setAvatarUrl("avatars/other/abc.jpg");
    assertNull(UserServiceImpl.publicAvatarUrl(user));
  }

  @Test
  void publicAvatarUrl_includesEncodedCacheBust() {
    UUID id = UUID.randomUUID();
    User user = new User();
    user.setId(id);
    user.setAvatarUrl("kchat/avatars/" + id + "/abc123_avatar.jpg");
    String url = UserServiceImpl.publicAvatarUrl(user);
    assertTrue(url.startsWith("/users/" + id + "/avatar?v="));
    assertTrue(url.contains("abc123_avatar.jpg"));
  }
}
