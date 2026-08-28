package com.kchat.media;

import java.util.Locale;
import java.util.Set;

/** Validation helpers for profile avatar uploads. */
public final class AvatarRules {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    public static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private AvatarRules() {
    }

    public static boolean isAllowedMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return false;
        }
        return ALLOWED_MIME.contains(mime.trim().toLowerCase(Locale.ROOT));
    }
}
