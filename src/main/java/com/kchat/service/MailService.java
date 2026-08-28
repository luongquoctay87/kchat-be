package com.kchat.service;

public interface MailService {

    void sendPasswordResetOtp(String toEmail, String displayName, String otp, int ttlMinutes);

    void sendRegistrationOtp(String toEmail, String displayName, String otp, int ttlMinutes);
}
