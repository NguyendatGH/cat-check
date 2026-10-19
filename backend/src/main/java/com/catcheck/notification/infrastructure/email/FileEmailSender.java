package com.catcheck.notification.infrastructure.email;

import com.catcheck.notification.api.EmailMessage;
import com.catcheck.notification.api.EmailSender;
import com.catcheck.notification.domain.OutgoingEmail;
import com.catcheck.notification.domain.port.EmailTransport;
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

@Component
@ConditionalOnProperty(
        name = "catcheck.notification.email-sink",
        havingValue = "file",
        matchIfMissing = true)
public class FileEmailSender implements EmailSender, EmailTransport {

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
            deliver(new OutgoingEmail(message.recipientEmail(), message.templateKey(),
                    message.locale(), message.variables()));
        } catch (RuntimeException ex) {
            log.warn("Ghi email ra file that bai (template={}): {}",
                    message.templateKey(), ex.getMessage());
        }
    }

    @Override
    public void deliver(OutgoingEmail email) {
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(fileNameFor(email.templateCode(), clock.millis())),
                    render(email), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static String fileNameFor(String templateCode, long epochMilli) {
        return "%d-%s.txt".formatted(epochMilli, templateCode.replaceAll("[^A-Za-z0-9._-]", "_"));
    }

    private String render(OutgoingEmail email) {
        StringBuilder out = new StringBuilder()
                .append("To: ").append(email.recipientEmail()).append('\n')
                .append("Template: ").append(email.templateCode()).append('\n')
                .append("Locale: ").append(email.locale()).append('\n')
                .append("Sent: ").append(clock.instant()).append('\n')
                .append('\n');
        email.variables().forEach((key, value) ->
                out.append(key).append('=')
                        .append(value == null ? "" : String.valueOf(value))
                        .append('\n'));
        return out.toString();
    }
}
