package com.kchat.service;

import com.kchat.common.dto.auth.AuthResponse;
import com.kchat.common.dto.auth.ChangePasswordRequest;
import com.kchat.common.dto.auth.LoginRequest;
import com.kchat.common.dto.auth.LogoutRequest;
import com.kchat.common.dto.auth.RefreshRequest;
import com.kchat.common.dto.auth.RegisterRequest;
import com.kchat.common.dto.auth.VerifyRegistrationOtpResponse;
import com.kchat.common.dto.auth.VerifyResetOtpResponse;
import java.util.UUID;

public interface AuthService {

  void sendRegistrationOtp(String email);

  VerifyRegistrationOtpResponse verifyRegistrationOtp(String email, String otp);

  AuthResponse register(RegisterRequest request, String deviceToken);

  AuthResponse login(LoginRequest request, String deviceToken);

  AuthResponse refresh(RefreshRequest request, String deviceToken);

  void logout(LogoutRequest request);

  void forgotPassword(String email);

  VerifyResetOtpResponse verifyResetOtp(String email, String otp);

  void resetPassword(String token, String newPassword);

  void changePassword(UUID userId, ChangePasswordRequest request);
}
