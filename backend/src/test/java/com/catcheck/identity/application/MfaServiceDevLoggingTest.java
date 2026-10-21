package com.catcheck.identity.application;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.CryptoPurpose;
import com.catcheck.identity.api.PiiCipher;
import com.catcheck.identity.domain.MfaTotp;
import com.catcheck.identity.domain.TotpCodeVerifier;
import com.catcheck.identity.domain.TotpStatus;
import com.catcheck.identity.domain.port.MfaRecoveryCodeRepository;
import com.catcheck.identity.domain.port.MfaTotpRepository;
import com.catcheck.identity.domain.port.RateLimiter;
import com.catcheck.identity.domain.port.RecoveryCodeHasher;
import com.catcheck.identity.domain.port.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MfaServiceDevLoggingTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:05Z");
    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Test
    void logsCurrentMaskedTotpWhenDevSwitchIsEnabled() {
        MfaTotpRepository totps = mock(MfaTotpRepository.class);
        PiiCipher cipher = mock(PiiCipher.class);
        MfaTotp active = activeTotp();
        when(totps.findByUserId(USER_ID)).thenReturn(Optional.of(active));
        when(cipher.decrypt(any(CryptoPurpose.class), any(), any()))
                .thenReturn(SECRET.getBytes(StandardCharsets.UTF_8));

        Logger logger = (Logger) LoggerFactory.getLogger(MfaService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service(totps, cipher, true).logCurrentTotpForDev(USER_ID, "admin@catcheck.vn");
        } finally {
            logger.detachAppender(appender);
        }

        String expectedCode = TotpCodeVerifier.now("SHA1", 6, SECRET, NOW.getEpochSecond() / 30);
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains("[DEV-TOTP]", "a***@catcheck.vn", "code=" + expectedCode,
                            "validForSeconds=25")
                    .doesNotContain(SECRET);
        });
    }

    @Test
    void doesNotReadTotpWhenDevSwitchIsDisabled() {
        MfaTotpRepository totps = mock(MfaTotpRepository.class);

        service(totps, mock(PiiCipher.class), false)
                .logCurrentTotpForDev(USER_ID, "admin@catcheck.vn");

        verify(totps, never()).findByUserId(any());
    }

    private static MfaService service(MfaTotpRepository totps, PiiCipher cipher, boolean enabled) {
        return new MfaService(
                totps,
                mock(MfaRecoveryCodeRepository.class),
                mock(UserRoleRepository.class),
                cipher,
                mock(RecoveryCodeHasher.class),
                mock(RateLimiter.class),
                mock(AuditLogService.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                enabled,
                true);
    }

    private static MfaTotp activeTotp() {
        return new MfaTotp(
                USER_ID, new byte[] {1, 2, 3}, TotpStatus.ACTIVE, "SHA1", 6, 30,
                null, 0, null, null, NOW.minusSeconds(60), 1,
                null, null, NOW.minusSeconds(120), NOW.minusSeconds(60));
    }
}
