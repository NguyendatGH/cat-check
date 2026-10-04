package com.catcheck.notification.infrastructure.email;

import com.catcheck.notification.api.EmailMessage;
import com.catcheck.notification.api.EmailSender;
import com.catcheck.notification.domain.OutgoingEmail;
import com.catcheck.notification.domain.port.EmailTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Gui email qua SMTP that. Bat khi {@code catcheck.notification.email-sink=smtp}.
 *
 * <p>Chi gui <b>ban text thuan</b>: template HTML nen duoc render o tang tren roi
 * gui multipart, con o day chi phu cap muc do "co mail ra". Muc dich la giup
 * module khac khong phai bi phu thuoc vao {@code javax.mail} — khi can HTML/attachment
 * se bo sung o day.</p>
 *
 * <p><b>Hai hop dong, co chu y:</b> {@link #send} nuot loi (hop dong cu cua
 * {@code EmailSender}: SMTP treo khong duoc lam hong luong dang ky — p11 §11.2.1), con
 * {@link #deliver} NEM. Job outbox goi {@code deliver} vi neu khong biet lan gui co that bai
 * hay khong thi backoff/dead-letter cua p12 §12.8.1 vo nghia.</p>
 */
@Component
@ConditionalOnProperty(name = "catcheck.notification.email-sink", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender, EmailTransport {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;

    public SmtpEmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            deliver(new OutgoingEmail(message.recipientEmail(), message.templateKey(),
                    message.locale(), message.variables()));
        } catch (RuntimeException ex) {
            log.warn("Gui email that bai qua SMTP (template={}): {}",
                    message.templateKey(), ex.getMessage());
        }
    }

    @Override
    public void deliver(OutgoingEmail email) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(email.recipientEmail());
        mail.setSubject(email.templateCode());
        mail.setText(body(email));
        mailSender.send(mail);
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
