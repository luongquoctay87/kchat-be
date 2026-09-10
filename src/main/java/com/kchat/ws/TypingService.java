package com.kchat.ws;

import com.kchat.config.RedisChannels;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TypingService {

  private static final long TYPING_TTL_SECONDS = 5;

  private final StringRedisTemplate redis;

  public TypingService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /** Per-user key so one peer's TTL does not wipe another's typing flag. */
  public void markTyping(UUID roomId, UUID userId) {
    String key = RedisChannels.typingUserKey(roomId, userId);
    redis.opsForValue().set(key, "1", TYPING_TTL_SECONDS, TimeUnit.SECONDS);
  }

  public void clearTyping(UUID roomId, UUID userId) {
    redis.delete(RedisChannels.typingUserKey(roomId, userId));
  }
}
