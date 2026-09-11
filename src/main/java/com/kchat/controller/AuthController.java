package com.kchat.controller;

import com.kchat.common.dto.auth.AuthResponse;
import com.kchat.common.dto.auth.AvailabilityResponse;
import com.kchat.common.dto.auth.ChangePasswordRequest;
import com.kchat.common.dto.auth.EmailRequest;
import com.kchat.common.dto.auth.LoginRequest;
import com.kchat.common.dto.auth.LogoutRequest;
import com.kchat.common.dto.auth.RefreshRequest;
import com.kchat.common.dto.auth.RegisterRequest;
import com.kchat.common.dto.auth.ResetPasswordRequest;
import com.kchat.common.dto.auth.VerifyOtpRequest;
import com.kchat.common.dto.auth.VerifyRegistrationOtpResponse;
import com.kchat.common.dto.auth.VerifyResetOtpResponse;
import com.kchat.security.DeviceSessionFilter;
import com.kchat.security.SecurityUtils;
import com.kchat.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@SecurityRequirements // public — clear global bearer requirement
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/send-registration-otp")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void sendRegistrationOtp(@Valid @RequestBody EmailRequest request) {
    authService.sendRegistrationOtp(request.email());
  }

  @PostMapping("/verify-registration-otp")
  public VerifyRegistrationOtpResponse verifyRegistrationOtp(
      @Valid @RequestBody VerifyOtpRequest request) {
    return authService.verifyRegistrationOtp(request.email(), request.otp());
  }

  @GetMapping("/check-username")
  public AvailabilityResponse checkUsername(@RequestParam("username") String username) {
    return authService.checkUsername(username);
  }

  @GetMapping("/check-email")
  public AvailabilityResponse checkEmail(@RequestParam("email") String email) {
    return authService.checkEmail(email);
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(
      @Valid @RequestBody RegisterRequest request,
      @RequestHeader(value = DeviceSessionFilter.DEVICE_TOKEN_HEADER, required = false)
          String deviceToken) {
    return authService.register(request, deviceToken);
  }

  @PostMapping("/login")
  public AuthResponse login(
      @Valid @RequestBody LoginRequest request,
      @RequestHeader(value = DeviceSessionFilter.DEVICE_TOKEN_HEADER, required = false)
          String deviceToken) {
    return authService.login(request, deviceToken);
  }

  @PostMapping("/refresh")
  public AuthResponse refresh(
      @Valid @RequestBody RefreshRequest request,
      @RequestHeader(value = DeviceSessionFilter.DEVICE_TOKEN_HEADER, required = false)
          String deviceToken) {
    return authService.refresh(request, deviceToken);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@Valid @RequestBody LogoutRequest request) {
    authService.logout(request);
  }

  @PostMapping("/forgot-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void forgotPassword(@Valid @RequestBody EmailRequest request) {
    authService.forgotPassword(request.email());
  }

  @PostMapping("/verify-reset-otp")
  public VerifyResetOtpResponse verifyResetOtp(@Valid @RequestBody VerifyOtpRequest request) {
    return authService.verifyResetOtp(request.email(), request.otp());
  }

  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request.token(), request.newPassword());
  }

  @PostMapping("/change-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    authService.changePassword(SecurityUtils.requireUserId(), request);
  }
}
