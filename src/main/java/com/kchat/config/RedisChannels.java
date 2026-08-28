package com.kchat.config;

/**
 * Redis channel / key names for realtime fan-out.
 */
public final class RedisChannels {

    public static final String ROOM_PREFIX = "kchat:pubsub:room:";
    public static final String ROOM_PATTERN = "kchat:pubsub:room:*";
    public static final String PRESENCE = "kchat:pubsub:presence";
    public static final String ONLINE_SET = "kchat:presence:online";
    public static final String TYPING_PREFIX = "kchat:typing:";

    private RedisChannels() {
    }

    public static String roomChannel(java.util.UUID roomId) {
        return ROOM_PREFIX + roomId;
    }

    /** Set of user ids currently typing in a room (short TTL). */
    public static String typingKey(java.util.UUID roomId) {
        return TYPING_PREFIX + roomId;
    }

    public static String typingUserKey(java.util.UUID roomId, java.util.UUID userId) {
        return TYPING_PREFIX + roomId + ":" + userId;
    }
}
