package com.kchat.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "kchat.fcm.enabled", havingValue = "true")
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    FirebaseApp firebaseApp(FcmProperties properties) throws IOException {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("kchat.fcm.enabled=true but service-account-json is empty");
        }
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new ByteArrayInputStream(properties.getServiceAccountJson().getBytes(StandardCharsets.UTF_8))
        );
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();
        if (FirebaseApp.getApps().isEmpty()) {
            log.info("Initializing Firebase Admin for FCM");
            return FirebaseApp.initializeApp(options);
        }
        return FirebaseApp.getInstance();
    }
}
