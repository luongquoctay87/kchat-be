package com.kchat.service.impl;

import com.kchat.common.exception.ApiException;
import com.kchat.config.MailBrandingProperties;
import com.kchat.mail.MailTemplateRenderer;
import com.kchat.mail.MailTemplateRenderer.OtpMail;
import com.kchat.mail.MailTemplateRenderer.RenderedMail;
import com.kchat.service.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailServiceImpl implements MailService {

  private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);
  private static final ClassPathResource LOGO =
      new ClassPathResource("templates/mail/kchat-icon.png");

  private final JavaMailSender mailSender;
  private final MailTemplateRenderer mailTemplateRenderer;
  private final MailBrandingProperties branding;
  private final String fromAddress;
  private final boolean smtpConfigured;
  private final boolean logOtpFallback;

  public MailServiceImpl(
      @Autowired(required = false) JavaMailSender mailSender,
      MailTemplateRenderer mailTemplateRenderer,
      MailBrandingProperties branding,
      @Value("${spring.mail.username:}") String fromAddress,
      @Value("${kchat.password-reset.mail-enabled:false}") boolean mailEnabled,
      Environment environment) {
    this.mailSender = mailSender;
    this.mailTemplateRenderer = mailTemplateRenderer;
    this.branding = branding;
    this.fromAddress = fromAddress == null ? "" : fromAddress.trim();
    this.smtpConfigured = mailEnabled && mailSender != null && !this.fromAddress.isBlank();
    this.logOtpFallback = environment.acceptsProfiles(Profiles.of("dev"));
  }

  @Override
  public void sendPasswordResetOtp(String toEmail, String displayName, String otp, int ttlMinutes) {
    send(
        OtpMail.passwordReset(
            branding.appName(),
            branding.companyName(),
            branding.supportEmail(),
            displayName,
            otp,
            ttlMinutes),
        toEmail,
        "Password reset");
  }

  @Override
  public void sendRegistrationOtp(String toEmail, String displayName, String otp, int ttlMinutes) {
    send(
        OtpMail.registration(
            branding.appName(),
            branding.companyName(),
            branding.supportEmail(),
            displayName,
            otp,
            ttlMinutes),
        toEmail,
        "Registration");
  }

  private void send(OtpMail model, String toEmail, String logLabel) {
    if (!smtpConfigured) {
      if (logOtpFallback) {
        log.info(
            "[k-chat] {} OTP for {} ({}): {} (expires in {} min — enable SMTP or copy OTP for dev)",
            logLabel,
            toEmail,
            model.displayName(),
            model.otp(),
            model.ttlMinutes());
        return;
      }
      throw ApiException.serviceUnavailable("mail_unavailable", "Email delivery is not configured");
    }

    RenderedMail rendered = mailTemplateRenderer.renderOtp(model);
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(fromAddress, branding.fromName());
      helper.setTo(toEmail);
      helper.setSubject(branding.appName() + " — " + model.subjectSuffix());
      helper.setText(rendered.text(), rendered.html());
      helper.addInline(MailTemplateRenderer.LOGO_CONTENT_ID, LOGO, "image/png");
      mailSender.send(message);
      log.debug("{} OTP email sent to {}", logLabel, toEmail);
    } catch (MessagingException | UnsupportedEncodingException ex) {
      log.error("Failed to send {} email to {}", logLabel, toEmail);
      throw ApiException.serviceUnavailable("mail_unavailable", "Could not send email");
    }
  }
}
