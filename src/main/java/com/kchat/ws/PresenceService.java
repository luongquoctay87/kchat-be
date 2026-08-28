package com.kchat.ws;

import com.kchat.config.RedisChannels;
import com.kchat.entity.UserSettings;
import com.kchat.repository.UserRepository;
import com.kchat.repository.UserSettingsRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PresenceService {

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    public PresenceService(
            StringRedisTemplate redis,
            UserRepository userRepository,
            UserSettingsRepository userSettingsRepository
    ) {
        this.redis = redis;
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
    }

    /** Whether this user allows others to see their online status. */
    public boolean canShowOnline(UUID userId) {
        return userSettingsRepository.findById(userId)
                .map(UserSettings::isShowOnline)
                .orElse(true);
    }

    /**
     * Online users whose {@code show_online} privacy allows visibility to others.
     */
    public Set<UUID> filterVisibleOnline(Collection<UUID> userIds) {
        Set<UUID> online = new HashSet<>(filterOnline(userIds));
        if (online.isEmpty()) {
            return Set.of();
        }
        for (UserSettings settings : userSettingsRepository.findAllById(online)) {
            if (!settings.isShowOnline()) {
                online.remove(settings.getUserId());
            }
        }
        return Set.copyOf(online);
    }

    /**
     * @return true if this call newly marked the user online (was not in the set)
     */
    public boolean markOnline(UUID userId) {
        Long added = redis.opsForSet().add(RedisChannels.ONLINE_SET, userId.toString());
        redis.expire(RedisChannels.ONLINE_SET, 2, TimeUnit.DAYS);
        return added != null && added > 0;
    }

    public void markOffline(UUID userId) {
        redis.opsForSet().remove(RedisChannels.ONLINE_SET, userId.toString());
    }

    public boolean isOnline(UUID userId) {
        Boolean member = redis.opsForSet().isMember(RedisChannels.ONLINE_SET, userId.toString());
        return Boolean.TRUE.equals(member);
    }

    /** Batch online check — one round-trip via pipeline. */
    public java.util.Set<UUID> filterOnline(java.util.Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Set.of();
        }
        java.util.List<UUID> ids = java.util.List.copyOf(userIds);
        java.util.List<Object> results = redis.executePipelined(
                (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                    byte[] key = RedisChannels.ONLINE_SET.getBytes();
                    for (UUID id : ids) {
                        connection.setCommands().sIsMember(key, id.toString().getBytes());
                    }
                    return null;
                }
        );
        java.util.Set<UUID> online = new java.util.HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            Object result = results.get(i);
            if (Boolean.TRUE.equals(result)) {
                online.add(ids.get(i));
            }
        }
        return java.util.Collections.unmodifiableSet(online);
    }

    @Transactional
    public void touchLastSeen(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastSeenAt(Instant.now());
            userRepository.save(user);
        });
    }
}
