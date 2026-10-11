package com.catcheck.notification.infrastructure.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.catcheck.notification.api.EmailMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/** Lỗi SMTP thường chép nguyên địa chỉ nhận vào message — không được đi vào log. */
class SmtpEmailSenderTest {

    @Test
    void swallowedSmtpFailureIsLoggedWithoutTheRecipientAddress() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("550 5.1.1 <person@example.com>: Recipient address rejected"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        Logger logger = (Logger) LoggerFactory.getLogger(SmtpEmailSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new SmtpEmailSender(mailSender).send(
                    EmailMessage.of("person@example.com", "EMAIL_OTP", "vi", Map.of()));

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains("MailSendException");
                assertThat(event.getFormattedMessage()).doesNotContain("person@example.com");
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
