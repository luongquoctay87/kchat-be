package com.kchat.service.impl;

import com.kchat.common.dto.auth.AuthResponse;
import com.kchat.common.dto.auth.ChangePasswordRequest;
import com.kchat.common.dto.auth.LoginRequest;
import com.kchat.common.dto.auth.LogoutRequest;
import com.kchat.common.dto.auth.RefreshRequest;
import com.kchat.common.dto.auth.RegisterRequest;
import com.kchat.common.dto.auth.VerifyRegistrationOtpResponse;
import com.kchat.common.dto.auth.VerifyResetOtpResponse;
import com.kchat.common.enums.UserStatus;
import com.kchat.common.exception.ApiException;
import com.kchat.config.JwtProperties;
import com.kchat.config.PasswordResetProperties;
import com.kchat.entity.PasswordResetToken;
import com.kchat.entity.RefreshToken;
import com.kchat.entity.RegistrationOtpToken;
import com.kchat.entity.User;
import com.kchat.entity.UserDevice;
import com.kchat.entity.UserSettings;
import com.kchat.repository.PasswordResetTokenRepository;
import com.kchat.repository.RefreshTokenRepository;
import com.kchat.repository.RegistrationOtpTokenRepository;
import com.kchat.repository.UserDeviceRepository;
import com.kchat.repository.UserRepository;
import com.kchat.repository.UserSettingsRepository;
import com.kchat.security.JwtService;
import com.kchat.security.TokenHasher;
import com.kchat.service.AuthService;
import com.kchat.service.ChannelEnrollmentService;
import com.kchat.service.MailService;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

  /**
   * BCrypt hash used only to keep login timing similar when the user is missing (value = bcrypt of
   * "timing-dummy-password").
   */
  private static final String DUMMY_PASSWORD_HASH =
      "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

  private static final String USERNAME_ONLY_EMAIL_SUFFIX = "@register.kchat.internal";

  private static final SecureRandom OTP_RANDOM = new SecureRandom();

  private final UserRepository userRepository;
  private final UserSettingsRepository userSettingsRepository;
  private final UserDeviceRepository userDeviceRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final RegistrationOtpTokenRepository registrationOtpTokenRepository;
  private final MailService mailService;
  private final PasswordResetProperties passwordResetProperties;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final JwtProperties jwtProperties;
  private final ChannelEnrollmentService channelEnrollmentService;

  public AuthServiceImpl(
      UserRepository userRepository,
      UserSettingsRepository userSettingsRepository,
      UserDeviceRepository userDeviceRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      RegistrationOtpTokenRepository registrationOtpTokenRepository,
      MailService mailService,
      PasswordResetProperties passwordResetProperties,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      JwtProperties jwtProperties,
      ChannelEnrollmentService channelEnrollmentService) {
    this.userRepository = userRepository;
    this.userSettingsRepository = userSettingsRepository;
    this.userDeviceRepository = userDeviceRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.registrationOtpTokenRepository = registrationOtpTokenRepository;
    this.mailService = mailService;
    this.passwordResetProperties = passwordResetProperties;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.jwtProperties = jwtProperties;
    this.channelEnrollmentService = channelEnrollmentService;
  }

  @Override
  public void sendRegistrationOtp(String email) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    if (normalized.endsWith(USERNAME_ONLY_EMAIL_SUFFIX)) {
      throw ApiException.badRequest("validation_error", "Email không hợp lệ");
    }
    if (userRepository.existsByEmailIgnoreCase(normalized)) {
      throw ApiException.conflict("Email already registered");
    }

    registrationOtpTokenRepository.markAllUsedForEmail(normalized);

    String otp = generateOtp(passwordResetProperties.otpLength());
    RegistrationOtpToken token = new RegistrationOtpToken();
    token.setEmail(normalized);
    token.setOtpHash(TokenHasher.sha256Hex(otp));
    token.setExpiresAt(
        Instant.now().plus(passwordResetProperties.ttlMinutes(), ChronoUnit.MINUTES));
    registrationOtpTokenRepository.save(token);

    String displayName = normalized.substring(0, normalized.indexOf('@'));
    mailService.sendRegistrationOtp(
        normalized, displayName, otp, passwordResetProperties.ttlMinutes());
  }

  @Override
  public VerifyRegistrationOtpResponse verifyRegistrationOtp(String email, String otp) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    String normalizedOtp = otp.trim();

    RegistrationOtpToken stored =
        registrationOtpTokenRepository
            .findFirstByEmailIgnoreCaseAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                normalizedEmail, Instant.now())
            .orElseThrow(this::invalidOtp);

    if (!TokenHasher.sha256Hex(normalizedOtp).equals(stored.getOtpHash())) {
      throw invalidOtp();
    }

    String plainRegistrationToken = TokenHasher.newRefreshToken();
    stored.setVerifiedAt(Instant.now());
    stored.setRegistrationTokenHash(TokenHasher.sha256Hex(plainRegistrationToken));
    registrationOtpTokenRepository.save(stored);

    return new VerifyRegistrationOtpResponse(plainRegistrationToken);
  }

  @Override
  public AuthResponse register(RegisterRequest request, String deviceToken) {
    String username = request.username().trim();
    String email = request.email().trim().toLowerCase(Locale.ROOT);

    RegistrationOtpToken otpToken =
        registrationOtpTokenRepository
            .findByRegistrationTokenHash(TokenHasher.sha256Hex(request.registrationToken().trim()))
            .filter(token -> token.getVerifiedAt() != null)
            .filter(RegistrationOtpToken::isUsable)
            .orElseThrow(
                () ->
                    ApiException.badRequest(
                        "invalid_registration_token", "Invalid or expired registration token"));

    if (!otpToken.getEmail().equalsIgnoreCase(email)) {
      throw ApiException.badRequest("validation_error", "Email does not match verified address");
    }

    if (userRepository.existsByUsernameIgnoreCase(username)) {
      throw ApiException.conflict("Username already taken");
    }
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw ApiException.conflict("Email already registered");
    }

    User user = new User();
    user.setUsername(username);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setDisplayName(request.displayName().trim());
    user.setStatus(UserStatus.active);

    try {
      userRepository.saveAndFlush(user);
      userSettingsRepository.save(UserSettings.defaultsFor(user));
    } catch (DataIntegrityViolationException ex) {
      throw ApiException.conflict("Username or email already registered");
    }

    registrationOtpTokenRepository.markAllUsedForEmail(email);

    channelEnrollmentService.enrollUserInAllChannels(user);
    touchDevice(user, deviceToken);
    return issueTokens(user, deviceToken);
  }

  @Override
  public AuthResponse login(LoginRequest request, String deviceToken) {
    String identifier = request.identifier().trim();
    User user = userRepository.findByUsernameOrEmail(identifier).orElse(null);

    String hash = user != null ? user.getPasswordHash() : DUMMY_PASSWORD_HASH;
    boolean passwordOk = passwordEncoder.matches(request.password(), hash);

    if (user == null || !passwordOk) {
      throw ApiException.unauthorized("Invalid credentials");
    }
    if (!user.isActive()) {
      throw ApiException.forbidden("Account is locked");
    }

    user.setLastSeenAt(Instant.now());
    touchDevice(user, deviceToken);
    return issueTokens(user, deviceToken);
  }

  @Override
  public AuthResponse refresh(RefreshRequest request, String deviceToken) {
    RefreshToken stored = findUsableRefreshToken(request.refreshToken());
    User user = stored.getUser();
    if (!user.isActive()) {
      stored.setRevokedAt(Instant.now());
      throw ApiException.forbidden("Account is locked");
    }

    String normalized = normalizeDeviceToken(deviceToken);
    String boundDevice = stored.getDeviceId();
    if (normalized != null) {
      if (!userDeviceRepository.existsByUser_IdAndFcmToken(user.getId(), normalized)) {
        throw ApiException.unauthorized("Device session revoked");
      }
      if (boundDevice != null && !boundDevice.equals(normalized)) {
        throw ApiException.unauthorized("Invalid refresh token");
      }
    } else if (boundDevice != null) {
      throw ApiException.unauthorized("Device session revoked");
    }

    stored.setRevokedAt(Instant.now());
    touchDevice(user, deviceToken);
    return issueTokens(user, deviceToken);
  }

  @Override
  public void logout(LogoutRequest request) {
    refreshTokenRepository
        .findByTokenHashWithUser(TokenHasher.sha256Hex(request.refreshToken()))
        .filter(token -> token.getRevokedAt() == null)
        .ifPresent(token -> token.setRevokedAt(Instant.now()));
  }

  @Override
  public void forgotPassword(String email) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    if (normalized.endsWith(USERNAME_ONLY_EMAIL_SUFFIX)) {
      // Username-only accounts cannot reset via email — silent success (no enumeration).
      return;
    }

    userRepository
        .findByEmailIgnoreCase(normalized)
        .ifPresent(
            user -> {
              if (!user.isActive()) {
                return;
              }
              passwordResetTokenRepository.markAllUsedForUser(user.getId());

              String otp = generateOtp(passwordResetProperties.otpLength());
              PasswordResetToken resetToken = new PasswordResetToken();
              resetToken.setUser(user);
              resetToken.setTokenHash(TokenHasher.sha256Hex(otp));
              resetToken.setExpiresAt(
                  Instant.now().plus(passwordResetProperties.ttlMinutes(), ChronoUnit.MINUTES));
              passwordResetTokenRepository.save(resetToken);

              mailService.sendPasswordResetOtp(
                  user.getEmail(),
                  user.getDisplayName(),
                  otp,
                  passwordResetProperties.ttlMinutes());
            });
  }

  @Override
  public VerifyResetOtpResponse verifyResetOtp(String email, String otp) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    String normalizedOtp = otp.trim();

    User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
    if (user == null || !user.isActive()) {
      throw invalidOtp();
    }

    PasswordResetToken stored =
        passwordResetTokenRepository
            .findFirstByUser_IdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                user.getId(), Instant.now())
            .orElseThrow(this::invalidOtp);

    if (!TokenHasher.sha256Hex(normalizedOtp).equals(stored.getTokenHash())) {
      throw invalidOtp();
    }

    String plainResetToken = TokenHasher.newRefreshToken();
    stored.setVerifiedAt(Instant.now());
    stored.setResetTokenHash(TokenHasher.sha256Hex(plainResetToken));
    passwordResetTokenRepository.save(stored);

    return new VerifyResetOtpResponse(plainResetToken);
  }

  @Override
  public void resetPassword(String token, String newPassword) {
    PasswordResetToken stored =
        passwordResetTokenRepository
            .findByResetTokenHashWithUser(TokenHasher.sha256Hex(token.trim()))
            .filter(tokenEntity -> tokenEntity.getVerifiedAt() != null)
            .filter(PasswordResetToken::isUsable)
            .orElseThrow(
                () ->
                    ApiException.badRequest(
                        "invalid_reset_token", "Invalid or expired reset token"));

    User user = stored.getUser();
    if (!user.isActive()) {
      throw ApiException.forbidden("Account is locked");
    }

    user.setPasswordHash(passwordEncoder.encode(newPassword));
    stored.setUsedAt(Instant.now());
    passwordResetTokenRepository.markAllUsedForUser(user.getId());
    refreshTokenRepository.revokeAllActiveForUser(user.getId());
  }

  @Override
  public void changePassword(UUID userId, ChangePasswordRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> ApiException.unauthorized("User not found"));
    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw ApiException.badRequest("validation_error", "Mật khẩu hiện tại không đúng");
    }
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
  }

  private AuthResponse issueTokens(User user, String deviceToken) {
    String access = jwtService.createAccessToken(user.getId(), user.loginName());
    String refreshPlain = TokenHasher.newRefreshToken();

    RefreshToken refresh = new RefreshToken();
    refresh.setUser(user);
    refresh.setTokenHash(TokenHasher.sha256Hex(refreshPlain));
    refresh.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTtlDays(), ChronoUnit.DAYS));
    String normalizedDevice = normalizeDeviceToken(deviceToken);
    if (normalizedDevice != null) {
      refresh.setDeviceId(normalizedDevice);
    }
    refreshTokenRepository.save(refresh);

    return new AuthResponse(access, refreshPlain);
  }

  private void touchDevice(User user, String deviceToken) {
    String normalized = normalizeDeviceToken(deviceToken);
    if (normalized == null) {
      return;
    }
    UserDevice device = userDeviceRepository.findByFcmToken(normalized).orElseGet(UserDevice::new);
    device.setUser(user);
    device.setFcmToken(normalized);
    if (device.getDeviceName() == null || device.getDeviceName().isBlank()) {
      device.setDeviceName("Android");
    }
    device.setPlatform("android");
    device.setLastActiveAt(Instant.now());
    userDeviceRepository.save(device);
  }

  private static String normalizeDeviceToken(String deviceToken) {
    if (deviceToken == null || deviceToken.isBlank()) {
      return null;
    }
    return deviceToken.trim();
  }

  private RefreshToken findUsableRefreshToken(String rawToken) {
    return refreshTokenRepository
        .findByTokenHashWithUser(TokenHasher.sha256Hex(rawToken))
        .filter(RefreshToken::isUsable)
        .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
  }

  private String generateOtp(int length) {
    int bound = 1;
    for (int i = 0; i < length; i++) {
      bound *= 10;
    }
    return String.format("%0" + length + "d", OTP_RANDOM.nextInt(bound));
  }

  private ApiException invalidOtp() {
    return ApiException.badRequest("invalid_otp", "Invalid or expired OTP");
  }
}
