package com.catcheck.privacy.application;

import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.DsarChannel;
import com.catcheck.privacy.domain.DsarRequest;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.DsarStatus;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    void ownerCanReadTheirDsarByPublicRef() {
        UUID owner = UUID.randomUUID();
        DsarRequestPort dsarPort = mock(DsarRequestPort.class);
        when(dsarPort.findByPublicRef("DSAR-1")).thenReturn(Optional.of(request(owner)));

        assertThat(service(dsarPort).getByPublicRef(owner, "DSAR-1").userId()).isEqualTo(owner);
    }

    @Test
    void otherUserGetsNotFoundForSomeoneElsesDsar() {
        DsarRequestPort dsarPort = mock(DsarRequestPort.class);
        when(dsarPort.findByPublicRef("DSAR-1")).thenReturn(Optional.of(request(UUID.randomUUID())));

        assertThatThrownBy(() -> service(dsarPort).getByPublicRef(UUID.randomUUID(), "DSAR-1"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PrivacyErrorCode.DSAR_NOT_FOUND));
    }

    /** M2: DSAR không có chủ (user_id NULL) không được đọc bởi người dùng bất kỳ. */
    @Test
    void ownerlessDsarIsNotReadableByAnyUser() {
        DsarRequestPort dsarPort = mock(DsarRequestPort.class);
        when(dsarPort.findByPublicRef("DSAR-1")).thenReturn(Optional.of(request(null)));

        assertThatThrownBy(() -> service(dsarPort).getByPublicRef(UUID.randomUUID(), "DSAR-1"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(PrivacyErrorCode.DSAR_NOT_FOUND));
    }

    private static DsarService service(DsarRequestPort dsarPort) {
        return new DsarService(dsarPort, mock(UserAccountPort.class), (userId, scope) -> true,
                mock(HolidayCalendarPort.class), new UuidV7(Clock.systemUTC()),
                Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC));
    }

    private static DsarRequest request(UUID owner) {
        Instant at = Instant.parse("2026-10-01T00:00:00Z");
        return new DsarRequest(UUID.randomUUID(), "DSAR-1", owner, "contact@example.invalid",
                DsarRequestType.ACCESS_EXPORT, DsarChannel.SELF_SERVICE, DsarStatus.RECEIVED, null, null,
                at, at.plusSeconds(86_400), null, at.plusSeconds(30L * 86_400), null, null, false,
                null, null, null, null, null, null, null, at);
    }
}
