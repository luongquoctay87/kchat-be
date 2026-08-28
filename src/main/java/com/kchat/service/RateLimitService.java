package com.kchat.service;

import com.kchat.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis counters for public auth throttles. Keys use {@code kchat:rl:} so they
 * do not collide with kpay on the shared ElastiCache.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    public static final String LOGIN_IP = "kchat:rl:login:ip:";
    public static final int LOGIN_IP_LIMIT = 20;
    public static final Duration LOGIN_IP_TTL = Duration.ofMinutes(15);

    public static final String REGISTER_OTP_IP = "kchat:rl:reg-otp:ip:";
    public static final int REGISTER_OTP_IP_LIMIT = 5;
    public static final Duration REGISTER_OTP_IP_TTL = Duration.ofMinutes(15);

    public static final String REGISTER_IP = "kchat:rl:register:ip:";
    public static final int REGISTER_IP_LIMIT = 10;
    public static final Duration REGISTER_IP_TTL = Duration.ofMinutes(15);

    public static final String VERIFY_OTP_IP = "kchat:rl:verify-otp:ip:";
    public static final int VERIFY_OTP_IP_LIMIT = 20;
    public static final Duration VERIFY_OTP_IP_TTL = Duration.ofMinutes(15);

    public static final String REFRESH_IP = "kchat:rl:refresh:ip:";
    public static final int REFRESH_IP_LIMIT = 60;
    public static final Duration REFRESH_IP_TTL = Duration.ofMinutes(1);

    public static final String FORGOT_IP = "kchat:rl:forgot:ip:";
    public static final int FORGOT_IP_LIMIT = 5;
    public static final Duration FORGOT_IP_TTL = Duration.ofMinutes(15);

    public static final String RESET_IP = "kchat:rl:reset:ip:";
    public static final int RESET_IP_LIMIT = 10;
    public static final Duration RESET_IP_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long consume(String key, int limit, Duration ttl) {
        long count = bump(key, ttl);
        if (count > limit) {
            log.warn("Rate limit exceeded key={} count={} limit={}", key, count, limit);
            throw ApiException.tooManyRequests("Too many requests");
        }
        return count;
    }

    public long bump(String key, Duration ttl) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return count != null ? count : 0L;
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getRemoteAddr();
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        int zone = ip.indexOf('%');
        if (zone >= 0) {
            ip = ip.substring(0, zone);
        }
        if ("::1".equals(ip) || "https://example.net/id/garnet".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }
}
