package com.kchat.common.util;

import com.kchat.common.enums.RoomRoles;
import com.kchat.common.enums.RoomType;

/** Rules for fixed company channels (#16). */
public final class ChannelRules {

    private ChannelRules() {
    }

    public static boolean canPost(RoomType type, String role) {
        if (type != RoomType.channel) {
            return true;
        }
        return RoomRoles.isManager(role);
    }

    /** Display title with leading # for channels. */
    public static String displayTitle(String name, String slug) {
        String base = name != null && !name.isBlank() ? name.trim() : (slug != null ? slug.trim() : "");
        if (base.isBlank()) {
            return "#channel";
        }
        // Strip duplicate leading hashes then normalize to one.
        while (base.startsWith("#")) {
            base = base.substring(1).trim();
        }
        if (base.isBlank()) {
            return "#channel";
        }
        return "#" + base;
    }
}
