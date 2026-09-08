package com.kchat.ws;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WsSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WsSessionRegistry.class);

    private final Map<UUID, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public void add(UUID userId, WebSocketSession session) {
        sessionsByUser.computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void remove(UUID userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId, sessions);
        }
    }

    public boolean hasSessions(UUID userId) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        sessions.removeIf(s -> !s.isOpen());
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId, sessions);
            return false;
        }
        return true;
    }

    public void sendToUser(UUID userId, String json) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException | IllegalStateException ex) {
                log.warn("Failed to send WS to user {}: {}", userId, ex.getMessage());
                sessions.remove(session);
            }
        }
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId, sessions);
        }
    }

    public Set<UUID> onlineUserIds() {
        return Set.copyOf(sessionsByUser.keySet());
    }
}
