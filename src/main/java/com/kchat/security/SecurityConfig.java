package com.kchat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kchat.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final ObjectMapper objectMapper;
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final DeviceSessionFilter deviceSessionFilter;
  private final AuthRateLimitFilter authRateLimitFilter;
  private final Environment environment;

  public SecurityConfig(
      ObjectMapper objectMapper,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      DeviceSessionFilter deviceSessionFilter,
      AuthRateLimitFilter authRateLimitFilter,
      Environment environment) {
    this.objectMapper = objectMapper;
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.deviceSessionFilter = deviceSessionFilter;
    this.authRateLimitFilter = authRateLimitFilter;
    this.environment = environment;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
              if (!prod) {
                auth.requestMatchers(
                        "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll();
              }
              auth.requestMatchers(
                      HttpMethod.POST,
                      "/auth/send-registration-otp",
                      "/auth/verify-registration-otp",
                      "/auth/register",
                      "/auth/login",
                      "/auth/refresh",
                      "/auth/logout",
                      "/auth/forgot-password",
                      "/auth/verify-reset-otp",
                      "/auth/reset-password")
                  .permitAll()
                  .requestMatchers(HttpMethod.GET, "/auth/check-username", "/auth/check-email")
                  .permitAll()
                  .requestMatchers(HttpMethod.POST, "/hooks/**")
                  .permitAll()
                  // Handshake still requires Bearer via JwtAuthenticationFilter / interceptor
                  .requestMatchers("/ws")
                  .authenticated()
                  .anyRequest()
                  .authenticated();
            })
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(authenticationEntryPoint())
                    .accessDeniedHandler(accessDeniedHandler()))
        .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(deviceSessionFilter, JwtAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private AuthenticationEntryPoint authenticationEntryPoint() {
    return (request, response, authException) ->
        writeError(response, HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required");
  }

  private AccessDeniedHandler accessDeniedHandler() {
    return (request, response, accessDeniedException) ->
        writeError(response, HttpStatus.FORBIDDEN, "forbidden", "Access denied");
  }

  private void writeError(
      HttpServletResponse response, HttpStatus status, String code, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(code, message));
  }
}
