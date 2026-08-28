package com.kchat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kchat.common.dto.call.IceServerDto;
import com.kchat.common.dto.call.IceServersResponse;
import com.kchat.config.WebRtcIceProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IceConfigServiceImplTest {

    @Test
    void stunOnlyWhenNoTurn() {
        WebRtcIceProperties props = new WebRtcIceProperties();
        props.setAllowDevOpenrelay(false);
        IceConfigServiceImpl svc = new IceConfigServiceImpl(props);
        IceServersResponse res = svc.iceServersForUser(UUID.randomUUID());
        assertEquals(1, res.iceServers().size());
        assertTrue(res.iceServers().getFirst().urls().getFirst().startsWith("stun:"));
        assertEquals(null, res.iceServers().getFirst().username());
    }

    @Test
    void ephemeralTurnUsesSharedSecret() {
        WebRtcIceProperties props = new WebRtcIceProperties();
        props.setTurnUrls(List.of("turn:turn.example.com:3478"));
        props.setTurnSharedSecret("test-secret");
        props.setTurnCredentialTtlSeconds(3600);
        props.setAllowDevOpenrelay(false);
        IceConfigServiceImpl svc = new IceConfigServiceImpl(props);
        UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        IceServersResponse res = svc.iceServersForUser(userId);
        assertEquals(2, res.iceServers().size());
        IceServerDto turn = res.iceServers().get(1);
        assertTrue(turn.username().endsWith(":" + userId));
        assertNotNull(turn.credential());
        assertFalse(turn.credential().isBlank());
        assertEquals(3600L, res.ttlSeconds());
    }

    @Test
    void devOpenrelayFallback() {
        WebRtcIceProperties props = new WebRtcIceProperties();
        props.setAllowDevOpenrelay(true);
        IceConfigServiceImpl svc = new IceConfigServiceImpl(props);
        IceServersResponse res = svc.iceServersForUser(UUID.randomUUID());
        assertTrue(res.iceServers().stream().anyMatch(s ->
                s.urls().stream().anyMatch(u -> u.contains("openrelay"))));
    }
}
