package com.catcheck.notification.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catcheck.notification.domain.OutgoingEmail;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailSenderHtmlTest {

    @Test
    void templateWithHtmlIsSentAsHtmlMessageWithRenderedSubjectAndFrom() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        SmtpEmailSender sender = new SmtpEmailSender(mailSender,
                new EmailTemplateRenderer("http://localhost:5173"), "catcheck@example.com");

        sender.deliver(new OutgoingEmail("person@example.com", "AUTH_OTP_REGISTER", "vi",
                Map.<String, Object>of("code", "482901", "expiresInMinutes", "5")));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        assertThat(message.getSubject()).isEqualTo("Mã xác thực đăng ký CatCheck");
        assertThat(message.getFrom()[0].toString()).contains("catcheck@example.com");
        var wire = new java.io.ByteArrayOutputStream();
        message.writeTo(wire);
        String raw = wire.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(raw).contains("Content-Type: text/html").contains("482901");
    }

    @Test
    void templateWithoutHtmlStillFallsBackToPlainTextMail() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailSender sender = new SmtpEmailSender(mailSender,
                new EmailTemplateRenderer("http://localhost:5173"), "catcheck@example.com");

        sender.deliver(new OutgoingEmail("person@example.com", "EMAIL_OTP", "vi", Map.<String, Object>of()));

        verify(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }
}
