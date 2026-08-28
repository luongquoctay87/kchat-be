package com.kchat.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kchat.common.enums.RoomRoles;
import com.kchat.common.enums.RoomType;
import com.kchat.entity.User;
import com.kchat.service.ChannelEnrollmentService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChannelRulesTest {

    @Test
    void canPost_directAndGroup_alwaysTrue() {
        assertTrue(ChannelRules.canPost(RoomType.direct, RoomRoles.MEMBER));
        assertTrue(ChannelRules.canPost(RoomType.group, RoomRoles.MEMBER));
    }

    @Test
    void canPost_channel_onlyManagers() {
        assertTrue(ChannelRules.canPost(RoomType.channel, RoomRoles.OWNER));
        assertTrue(ChannelRules.canPost(RoomType.channel, RoomRoles.ADMIN));
        assertFalse(ChannelRules.canPost(RoomType.channel, RoomRoles.MEMBER));
        assertFalse(ChannelRules.canPost(RoomType.channel, null));
    }

    @Test
    void displayTitle_addsHashAndNormalizes() {
        assertEquals("#ops-alerts", ChannelRules.displayTitle("ops-alerts", "ops-alerts"));
        assertEquals("#ops-alerts", ChannelRules.displayTitle("#ops-alerts", "ops-alerts"));
        assertEquals("#ops-alerts", ChannelRules.displayTitle("##ops-alerts", null));
        assertEquals("#support", ChannelRules.displayTitle(null, "support"));
        assertEquals("#channel", ChannelRules.displayTitle(null, null));
    }

    @Test
    void pickOwner_prefersAdminUsername() {
        User a = new User();
        a.setUsername("nguyenva");
        User admin = new User();
        admin.setUsername("admin");
        assertEquals(admin, ChannelEnrollmentService.pickOwner(List.of(a, admin)).orElseThrow());
        assertEquals(a, ChannelEnrollmentService.pickOwner(List.of(a)).orElseThrow());
        assertTrue(ChannelEnrollmentService.pickOwner(List.of()).isEmpty());
    }
}
