package com.kchat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kchat.common.exception.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    StringRedisTemplate redisTemplate;
    @Mock
    ValueOperations<String, String> valueOps;
    @InjectMocks
    RateLimitService service;

    @Test
    void consumeIncrementsAndRejectsOverLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("kchat:rl:login:ip:1.1.1.1")).thenReturn(21L);

        ApiException ex = assertThrows(
                ApiException.class,
                () -> service.consume("kchat:rl:login:ip:1.1.1.1", 20, Duration.ofMinutes(15))
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals("rate_limited", ex.getCode());
    }

    @Test
    void bumpSetsTtlOnFirstHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("kchat:rl:z")).thenReturn(1L);
        assertEquals(1L, service.bump("kchat:rl:z", Duration.ofMinutes(15)));
        verify(redisTemplate).expire(eq("kchat:rl:z"), eq(Duration.ofMinutes(15)));
    }

    @Test
    void clientIpFallsBackToUnknown() {
        assertEquals("unknown", RateLimitService.clientIp(null));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.9");
        assertEquals("10.0.0.9", RateLimitService.clientIp(req));
        req.setRemoteAddr("::1%0");
        assertEquals("127.0.0.1", RateLimitService.clientIp(req));
    }

    @Test
    void keysAreNamespacedAwayFromKpay() {
        assertEquals("kchat:rl:login:ip:", RateLimitService.LOGIN_IP);
        assertEquals("kchat:rl:reg-otp:ip:", RateLimitService.REGISTER_OTP_IP);
    }
}
