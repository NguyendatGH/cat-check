package com.catcheck.notification;

import com.catcheck.notification.application.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bind thật khối {@code catcheck.notification} của {@code application.yml}.
 *
 * <p>Hai thứ test này bắt mà unit test không bắt:</p>
 * <ol>
 *   <li><b>Trộn namespace.</b> {@code catcheck.notification.email-sink} (một {@code @Value}
 *       riêng của {@code FileEmailSender}/{@code SmtpEmailSender}, bật ở profile {@code local})
 *       nằm CÙNG tiền tố với {@code @ConfigurationProperties}. Nếu binder không bỏ qua khoá lạ
 *       thì profile {@code local} không khởi động được — mà smoke test lại chạy profile
 *       {@code test} nên sẽ không ai thấy.</li>
 *   <li><b>Mặc định an toàn.</b> Thiếu {@code FIREBASE_SERVICE_ACCOUNT_PATH} phải ra
 *       {@code configured() == false} (push tắt có kiểm soát) chứ không phải chuỗi
 *       {@code "${...}"} chưa phân giải.</li>
 * </ol>
 */
class NotificationPropertiesBindingTest {

    private NotificationProperties bind(Map<String, Object> overrides) {
        MutablePropertySources sources = new MutablePropertySources();
        if (!overrides.isEmpty()) {
            sources.addFirst(new MapPropertySource("overrides", overrides));
        }
        sources.addLast(applicationYml());
        // PHẢI có placeholder resolver: giá trị trong YAML là chuỗi "${FIREBASE_SERVICE_ACCOUNT_PATH:}".
        // Binder trần sẽ bind nguyên văn chuỗi đó và `configured()` hoá ra true — ngược hẳn
        // hành vi thật, nơi Environment phân giải placeholder thành chuỗi rỗng khi thiếu biến.
        return new Binder(ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources))
                .bind("catcheck.notification", NotificationProperties.class)
                .orElseThrow(() -> new AssertionError("Không bind được catcheck.notification"));
    }

    private PropertySource<?> applicationYml() {
        try {
            return new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"))
                    .getFirst();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Test
    void outboxDefaultsMatchPart12() {
        NotificationProperties.Outbox outbox = bind(Map.of()).outbox();
        assertThat(outbox.maxAttempts()).isEqualTo(5);
        assertThat(outbox.emailBatchSize()).isEqualTo(50);
        assertThat(outbox.pushBatchSize()).isEqualTo(100);
        // Hai cờ `email-job-enabled`/`push-job-enabled` đã chuyển sang
        // `catcheck.jobs.send-email-outbox.enabled` / `catcheck.jobs.retry-failed-notifications
        // .enabled` ở W2-B (H15.73) — JobPropertiesBindingTest canh chúng.
    }

    @Test
    void pushIsDisabledWhenNoServiceAccountPathIsSupplied() {
        NotificationProperties.Push push = bind(Map.of()).push();
        assertThat(push.configured()).isFalse();
        assertThat(push.maxDevicesPerUser()).isEqualTo(10);
    }

    @Test
    void pushTurnsOnOnceTheServiceAccountPathIsSupplied() {
        NotificationProperties.Push push = bind(Map.of(
                "catcheck.notification.push.service-account-path", "/run/secrets/firebase-sa.json"))
                .push();
        assertThat(push.configured()).isTrue();
    }

    /** Khoá của profile `local`, không thuộc record — binder phải bỏ qua, không được ném. */
    @Test
    void unrelatedEmailSinkKeyUnderTheSamePrefixDoesNotBreakBinding() {
        NotificationProperties properties = bind(Map.of(
                "catcheck.notification.email-sink", "smtp",
                "catcheck.notification.email-sink-path", "target/dev-mail"));
        assertThat(properties.outbox().maxAttempts()).isEqualTo(5);
    }
}
