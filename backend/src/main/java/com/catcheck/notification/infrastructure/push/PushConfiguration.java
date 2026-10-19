package com.catcheck.notification.infrastructure.push;

import com.catcheck.notification.application.NotificationProperties;
import com.catcheck.notification.domain.port.PushMessageSender;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Configuration
public class PushConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PushConfiguration.class);
    private static final String APP_NAME = "catcheck";

    @Bean
    public PushMessageSender pushMessageSender(NotificationProperties properties) {
        NotificationProperties.Push push = properties.push();
        if (!push.configured()) {
            log.info("Push FCM: TẮT (chưa đặt catcheck.notification.push.service-account-path).");
            return new DisabledPushMessageSender();
        }
        Path credentialPath = Path.of(push.serviceAccountPath());
        if (!Files.isReadable(credentialPath)) {
            log.warn("Push FCM: TẮT — không đọc được file service account đã cấu hình.");
            return new DisabledPushMessageSender();
        }
        try (InputStream credentials = Files.newInputStream(credentialPath)) {
            FirebaseOptions.Builder options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials));
            if (push.projectId() != null && !push.projectId().isBlank()) {
                options.setProjectId(push.projectId());
            }
            FirebaseApp app = existingApp().orElseGet(() -> FirebaseApp.initializeApp(options.build(), APP_NAME));
            log.info("Push FCM: BẬT.");
            return new FirebaseCloudMessagingSender(FirebaseMessaging.getInstance(app));
        } catch (IOException | RuntimeException ex) {
            log.warn("Push FCM: TẮT — khởi tạo FirebaseApp thất bại ({}).", ex.getClass().getSimpleName());
            return new DisabledPushMessageSender();
        }
    }

    private java.util.Optional<FirebaseApp> existingApp() {
        List<FirebaseApp> apps = FirebaseApp.getApps();
        return apps.stream().filter(app -> APP_NAME.equals(app.getName())).findFirst();
    }
}
