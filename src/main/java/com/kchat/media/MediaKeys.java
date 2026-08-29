package com.kchat.media;

import java.util.UUID;

/**
 * S3 object keys stored in {@code message_attachments.s3_key},
 * {@code users.avatar_url}, and {@code rooms.avatar_url}.
 */
public final class MediaKeys {

    static final String PREFIX = "kchat";

    private MediaKeys() {
    }

    public static String roomKey(UUID roomId, String originalFileName) {
        return PREFIX + "/rooms/" + roomId + "/" + UUID.randomUUID() + "_"
                + sanitizeFileName(originalFileName);
    }

    public static String avatarKey(UUID userId, String originalFileName) {
        return PREFIX + "/avatars/" + userId + "/" + UUID.randomUUID() + "_"
                + sanitizeFileName(originalFileName);
    }

    public static String groupAvatarKey(UUID roomId, String originalFileName) {
        return PREFIX + "/group-avatars/" + roomId + "/" + UUID.randomUUID() + "_"
                + sanitizeFileName(originalFileName);
    }

    public static boolean isAvatarKey(UUID userId, String key) {
        if (userId == null || !isSafeKey(key)) {
            return false;
        }
        String prefix = PREFIX + "/avatars/" + userId + "/";
        return key.startsWith(prefix) && key.length() > prefix.length();
    }

    public static boolean isGroupAvatarKey(UUID roomId, String key) {
        if (roomId == null || !isSafeKey(key)) {
            return false;
        }
        String prefix = PREFIX + "/group-avatars/" + roomId + "/";
        return key.startsWith(prefix) && key.length() > prefix.length();
    }

    public static String sanitizeFileName(String original) {
        String name = original == null || original.isBlank() ? "file" : original;
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        return name.isBlank() ? "file" : name;
    }

    public static boolean isSafeKey(String key) {
        return key != null
                && !key.isBlank()
                && !key.contains("..")
                && !key.startsWith("/")
                && !key.contains("\\");
    }

    public static void requireSafeKey(String key) {
        if (!isSafeKey(key)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
    }
}
