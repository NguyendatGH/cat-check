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

/**
 * Dựng {@link PushMessageSender}.
 *
 * <p><b>Bật/tắt bằng đúng một thứ: có hay không file service account.</b>
 * {@code catcheck.notification.push.service-account-path} (biến môi trường
 * {@code FIREBASE_SERVICE_ACCOUNT_PATH}, p18 §18.9) — là <b>đường dẫn</b> tới file JSON mount
 * read-only, KHÔNG phải nội dung JSON (research §15.3 cấm nhét cả JSON vào env var).</p>
 *
 * <p><b>Thiếu/không đọc được ⇒ KHÔNG ném.</b> App phải khởi động được trên máy dev và trong CI,
 * nơi không bao giờ có khoá Firebase. Khi đó bean là {@link DisabledPushMessageSender}: log một
 * cảnh báo, các dòng {@code notification_outbox} giữ nguyên {@code PENDING}. Dựng
 * {@code FirebaseApp} thất bại cũng rơi về nhánh này — một khoá sai định dạng không được làm
 * sập cả ứng dụng, trong khi in-app và email vẫn chạy bình thường.</p>
 *
 * <p>Tên app Firebase đặt riêng ({@code catcheck}) thay vì {@code [DEFAULT]} để không tranh với
 * bất kỳ {@code FirebaseApp.initializeApp()} nào khác, và để khởi động lại context trong test
 * không ném {@code IllegalStateException} "app already exists".</p>
 */
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
