package com.catcheck.notification.infrastructure.email;

import com.catcheck.notification.api.EmailMessage;
import com.catcheck.notification.api.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Ghi email ra file thay vi gui SMTP — cho may dev va cho test khong can mang.
 *
 * <p><b>Khong ghi vao log.</b> Ma OTP va link kich hoat la du lieu nhay cam; p17
 * §17.10b co test CI chan PII trong log. Ghi ra file {@code target/dev-mail/} vua
 * dung cho dev (mo bang trinh duyet), vua khong bien log thanh vector DoS.</p>
 *
 * <p>Mac dinh la kich hoat ({@code catcheck.notification.email-sink=file}) de app luon
 * co dung mot {@link EmailSender}. O prod/staging, W3 chuyen sang
 * {@code catcheck.notification.email-sink=smtp}.</p>
 */
@Component
@ConditionalOnProperty(
        name = "catcheck.notification.email-sink",
        havingValue = "file",
        matchIfMissing = true)
public class FileEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(FileEmailSender.class);

    private final Path directory;
    private final Clock clock;

    public FileEmailSender(
            @Value("${catcheck.notification.email-sink-path:target/dev-mail}") String path,
            Clock clock) {
        this.directory = Path.of(path);
        this.clock = clock;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(fileNameFor(message, clock.millis())),
                    render(message), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            // Khong nem: module gui email khong duoc lam do mat luong dang ky.
            // Chi ghi loai loi (khong PII, khong noi dung email).
            log.warn("Ghi email ra file that bai (template={}): {}",
                    message.templateKey(), ex.getMessage());
        }
    }

    static String fileNameFor(EmailMessage message, long epochMilli) {
        return "%d-%s.txt".formatted(epochMilli,
                message.templateKey().replaceAll("[^A-Za-z0-9._-]", "_"));
    }

    private String render(EmailMessage message) {
        StringBuilder out = new StringBuilder()
                .append("To: ").append(message.recipientEmail()).append('\n')
                .append("Template: ").append(message.templateKey()).append('\n')
                .append("Locale: ").append(message.locale()).append('\n')
                .append("Sent: ").append(clock.instant()).append('\n')
                .append('\n');
        message.variables().forEach((key, value) ->
                out.append(key).append('=')
                        .append(value == null ? "" : String.valueOf(value))
                        .append('\n'));
        return out.toString();
    }
}
