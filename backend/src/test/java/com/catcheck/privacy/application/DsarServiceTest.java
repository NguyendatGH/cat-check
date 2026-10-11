package com.catcheck.privacy.application;

import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.port.DsarRequestPort;
import com.catcheck.privacy.domain.port.HolidayCalendarPort;
import com.catcheck.privacy.spi.StepUpVerificationPort;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DsarServiceTest {

    @Test
    void enablingRestrictionRequiresStepUpBeforeReadingOrChangingTheAccount() {
        DsarRequestPort dsarPort = mock(DsarRequestPort.class);
        UserAccountPort accountPort = mock(UserAccountPort.class);
        StepUpVerificationPort stepUp = (userId, scope) -> false;
        DsarService service = new DsarService(dsarPort, accountPort, stepUp, mock(HolidayCalendarPort.class),
                new UuidV7(Clock.systemUTC()), Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.enableRestriction(UUID.randomUUID(), null))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PrivacyErrorCode.DSAR_IDENTITY_VERIFICATION_REQUIRED));
        verifyNoInteractions(dsarPort, accountPort);
    }
}
