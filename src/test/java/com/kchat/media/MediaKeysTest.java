package com.kchat.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaKeysTest {

  @Test
  void roomAndAvatarKeysUsePrefix() {
    UUID roomId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String roomKey = MediaKeys.roomKey(roomId, "photo.jpg");
    String avatarKey = MediaKeys.avatarKey(userId, "me.png");
    assertTrue(roomKey.startsWith("kchat/rooms/" + roomId + "/"));
    assertTrue(roomKey.endsWith("_photo.jpg"));
    assertTrue(avatarKey.startsWith("kchat/avatars/" + userId + "/"));
    assertTrue(avatarKey.endsWith("_me.png"));
    assertTrue(MediaKeys.isAvatarKey(userId, avatarKey));
  }

  @Test
  void isAvatarKeyRejectsForeignAndUnsafe() {
    UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
    assertTrue(MediaKeys.isAvatarKey(userId, "kchat/avatars/" + userId + "/abc_avatar.jpg"));
    assertFalse(MediaKeys.isAvatarKey(userId, "avatars/" + userId + "/abc_avatar.jpg"));
    assertFalse(MediaKeys.isAvatarKey(userId, "kchat/avatars/" + userId + "/../secret"));
    assertFalse(MediaKeys.isAvatarKey(userId, "kchat/avatars/other/abc.jpg"));
    assertFalse(MediaKeys.isAvatarKey(userId, null));
  }

  @Test
  void sanitizeStripsPathAndUnsafeChars() {
    assertEquals("a_b.jpg", MediaKeys.sanitizeFileName("../../a b.jpg"));
    assertEquals("___", MediaKeys.sanitizeFileName("***"));
    assertEquals("file", MediaKeys.sanitizeFileName("   "));
  }

  @Test
  void requireSafeKeyRejectsTraversal() {
    assertThrows(IllegalArgumentException.class, () -> MediaKeys.requireSafeKey("../secret"));
    assertThrows(IllegalArgumentException.class, () -> MediaKeys.requireSafeKey("/etc/passwd"));
    MediaKeys.requireSafeKey("kchat/rooms/" + UUID.randomUUID() + "/a.jpg");
  }
}
