package com.kchat;

import com.kchat.config.BotProperties;
import com.kchat.config.DisappearingMessageCleanupProperties;
import com.kchat.config.FcmProperties;
import com.kchat.config.JwtProperties;
import com.kchat.config.MailBrandingProperties;
import com.kchat.config.MessageRetentionCleanupProperties;
import com.kchat.config.PasswordResetProperties;
import com.kchat.config.S3Properties;
import com.kchat.config.WebRtcIceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
  JwtProperties.class,
  BotProperties.class,
  DisappearingMessageCleanupProperties.class,
  MessageRetentionCleanupProperties.class,
  WebRtcIceProperties.class,
  PasswordResetProperties.class,
  MailBrandingProperties.class,
  S3Properties.class,
  FcmProperties.class
})
public class KChatApplication {

  public static void main(String[] args) {
    SpringApplication.run(KChatApplication.class, args);
  }
}
