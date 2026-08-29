package com.kchat.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaKeysGroupAvatarTest {

    @Test
    void groupAvatarKey_roundTrip() {
        UUID roomId = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        String key = MediaKeys.groupAvatarKey(roomId, "photo.jpg");
        assertTrue(key.startsWith("kchat/group-avatars/" + roomId + "/"));
        assertTrue(MediaKeys.isGroupAvatarKey(roomId, key));
        assertFalse(MediaKeys.isGroupAvatarKey(roomId, "kchat/rooms/" + roomId + "/x.jpg"));
        assertFalse(MediaKeys.isAvatarKey(roomId, key));
    }
}
