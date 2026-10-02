package com.catcheck.notification.infrastructure.email;

import com.catcheck.notification.api.EmailMessage;
import com.catcheck.notification.api.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Gui email qua SMTP thật. Bat khi {@code catcheck.notification.email-sink=smtp}.
 *
 * <p>Chi gui <b>ban text thuan</b>: template HTML nen duoc render o tang tren roi
 * gui multipart, con o day chi phu cap muc do "co mail ra". Muc dich la giup
 * module khac khong phai bi phu thuoc vao {@code javax.mail} — khi can HTML/attachment
 * se bo sung o day.</p>
 *
 * <p>Khong nem loi khi that bai: SMTP treo khong duoc lam hong luong dang ky/OTP
 * (p11 §11.2.1). Job doc {@code email_outbox} se thu lai (W3/M2 — bang outbox thuoc
 * wave sau, xem {@code docs/handovers/A1.md}).</p>
 */
@Component
@ConditionalOnProperty(name = "catcheck.notification.email-sink", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;

    public SmtpEmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(message.recipientEmail());
            mail.setSubject(message.templateKey());
            mail.setText(body(message));
            if (message.replyTo() != null) {
                mail.setReplyTo(message.replyTo().toString());
            }
            mailSender.send(mail);
        } catch (RuntimeException ex) {
            log.warn("Gui email that bai qua SMTP (template={}): {}",
                    message.templateKey(), ex.getMessage());
        }
    }

    private String body(EmailMessage message) {
        StringBuilder out = new StringBuilder();
        message.variables().forEach((key, value) -> out
                .append(key).append(": ")
                .append(value == null ? "" : String.valueOf(value))
                .append(System.lineSeparator()));
        return out.toString();
    }
}
