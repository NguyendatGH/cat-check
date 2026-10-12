package com.catcheck.notification.infrastructure.email;

import com.catcheck.notification.api.EmailMessage;
import com.catcheck.notification.api.EmailSender;
import com.catcheck.notification.domain.OutgoingEmail;
import com.catcheck.notification.domain.port.EmailTransport;
import com.catcheck.notification.infrastructure.email.EmailTemplateRenderer.RenderedEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "catcheck.notification.email-sink", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender, EmailTransport {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final EmailTemplateRenderer renderer;
    private final String fromAddress;

    public SmtpEmailSender(JavaMailSender mailSender,
                           EmailTemplateRenderer renderer,
                           @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.renderer = renderer;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            deliver(new OutgoingEmail(message.recipientEmail(), message.templateKey(),
                    message.locale(), message.variables()));
        } catch (RuntimeException ex) {
            log.warn("Gui email that bai qua SMTP (template={}): {}",
                    message.templateKey(), ex.getClass().getSimpleName());
        }
    }

    @Override
    public void deliver(OutgoingEmail email) {
        Optional<RenderedEmail> html = renderer.render(email.templateCode(), email.variables());
        if (html.isPresent()) {
            deliverHtml(email, html.get());
            return;
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(email.recipientEmail());
        mail.setSubject(email.templateCode());
        mail.setText(body(email));
        mailSender.send(mail);
    }

    private void deliverHtml(OutgoingEmail email, RenderedEmail rendered) {
        MimeMessage mime = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
            helper.setTo(email.recipientEmail());
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }
            helper.setSubject(rendered.subject());
            helper.setText(rendered.html(), true);
        } catch (MessagingException ex) {
            throw new MailPreparationException("Không dựng được email HTML (template="
                    + email.templateCode() + ")", ex);
        }
        mailSender.send(mime);
    }

    private String body(OutgoingEmail email) {
        StringBuilder out = new StringBuilder();
        email.variables().forEach((key, value) -> out
                .append(key).append(": ")
                .append(value == null ? "" : String.valueOf(value))
                .append(System.lineSeparator()));
        return out.toString();
    }
}
