package com.kchat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kchat.common.dto.ErrorResponse;
import com.kchat.common.exception.ApiException;
import com.kchat.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

  private record Policy(String prefix, int limit, Duration ttl) {}

  private static final Map<String, Policy> POLICIES =
      Map.of(
          "/auth/login",
              new Policy(
                  RateLimitService.LOGIN_IP,
                  RateLimitService.LOGIN_IP_LIMIT,
                  RateLimitService.LOGIN_IP_TTL),
          "/auth/send-registration-otp",
              new Policy(
                  RateLimitService.REGISTER_OTP_IP,
                  RateLimitService.REGISTER_OTP_IP_LIMIT,
                  RateLimitService.REGISTER_OTP_IP_TTL),
          "/auth/register",
              new Policy(
                  RateLimitService.REGISTER_IP,
                  RateLimitService.REGISTER_IP_LIMIT,
                  RateLimitService.REGISTER_IP_TTL),
          "/auth/verify-registration-otp",
              new Policy(
                  RateLimitService.VERIFY_OTP_IP,
                  RateLimitService.VERIFY_OTP_IP_LIMIT,
                  RateLimitService.VERIFY_OTP_IP_TTL),
          "/auth/verify-reset-otp",
              new Policy(
                  RateLimitService.VERIFY_OTP_IP,
                  RateLimitService.VERIFY_OTP_IP_LIMIT,
                  RateLimitService.VERIFY_OTP_IP_TTL),
          "/auth/refresh",
              new Policy(
                  RateLimitService.REFRESH_IP,
                  RateLimitService.REFRESH_IP_LIMIT,
                  RateLimitService.REFRESH_IP_TTL),
          "/auth/forgot-password",
              new Policy(
                  RateLimitService.FORGOT_IP,
                  RateLimitService.FORGOT_IP_LIMIT,
                  RateLimitService.FORGOT_IP_TTL),
          "/auth/reset-password",
              new Policy(
                  RateLimitService.RESET_IP,
                  RateLimitService.RESET_IP_LIMIT,
                  RateLimitService.RESET_IP_TTL));

  private final RateLimitService rateLimitService;
  private final ObjectMapper objectMapper;

  public AuthRateLimitFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
    this.rateLimitService = rateLimitService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!HttpMethod.POST.matches(request.getMethod())) {
      return true;
    }
    return !POLICIES.containsKey(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Policy policy = POLICIES.get(request.getRequestURI());
    try {
      rateLimitService.consume(
          policy.prefix() + RateLimitService.clientIp(request), policy.limit(), policy.ttl());
    } catch (ApiException ex) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectMapper.writeValue(
          response.getOutputStream(), new ErrorResponse(ex.getCode(), ex.getMessage()));
      return;
    }
    filterChain.doFilter(request, response);
  }
}
