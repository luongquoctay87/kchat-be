package com.kchat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kchat.common.dto.ErrorResponse;
import com.kchat.repository.UserDeviceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects authenticated requests when {@code X-Device-Token} is present but no longer registered
 * (e.g. after remote revoke from another device).
 */
@Component
public class DeviceSessionFilter extends OncePerRequestFilter {

    public static final String DEVICE_TOKEN_HEADER = "X-Device-Token";

    private final UserDeviceRepository userDeviceRepository;
    private final ObjectMapper objectMapper;

    public DeviceSessionFilter(UserDeviceRepository userDeviceRepository, ObjectMapper objectMapper) {
        this.userDeviceRepository = userDeviceRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
                || path.startsWith("/hooks/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        String deviceToken = normalizeDeviceToken(request.getHeader(DEVICE_TOKEN_HEADER));
        if (deviceToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isDeviceRegistrationRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!userDeviceRepository.existsByUser_IdAndFcmToken(principal.getId(), deviceToken)) {
            writeUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Allow first-time registration; blocking here prevents POST /devices from ever succeeding. */
    static boolean isDeviceRegistrationRequest(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/devices".equals(path) || path.endsWith("/devices");
    }

    private static String normalizeDeviceToken(String deviceToken) {
        if (deviceToken == null || deviceToken.isBlank()) {
            return null;
        }
        return deviceToken.trim();
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ErrorResponse("unauthorized", "Device session revoked")
        );
    }
}
