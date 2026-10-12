package com.catcheck.notification.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DeadPushTokenCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T08:00:00Z");

    private final NotificationTestDoubles.FakeSubscriptions repository =
            new NotificationTestDoubles.FakeSubscriptions();
    private final DeadPushTokenCleanupService service =
            new DeadPushTokenCleanupService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void retentionWindowsMatchPart12() {
        assertThat(DeadPushTokenCleanupService.REVOKED_RETENTION).isEqualTo(Duration.ofDays(30));
        assertThat(DeadPushTokenCleanupService.STALE_RETENTION).isEqualTo(Duration.ofDays(180));
    }

    @Test
    void deletingUpToTwentyPercentIsAllowed() {
        repository.totalCount = 100;
        repository.deadCount = 20;

        assertThat(service.survey(NOW).exceedsSafetyThreshold())
                .as("dung 20% KHONG vuot nguong — p15 REQ-RET-02 noi 'qua 20%'")
                .isFalse();
    }

    @Test
    void goingOverTwentyPercentStopsTheJob() {
        repository.totalCount = 100;
        repository.deadCount = 21;

        assertThat(service.survey(NOW).exceedsSafetyThreshold()).isTrue();
    }

    @Test
    void anEmptyTableIsNotAThresholdBreach() {
        repository.totalCount = 0;
        repository.deadCount = 0;

        assertThat(service.survey(NOW).exceedsSafetyThreshold()).isFalse();
    }

    @Test
    void deleteBatchHonoursTheLimit() {
        repository.totalCount = 10;
        repository.deadCount = 7;

        assertThat(service.deleteBatch(NOW, 5)).isEqualTo(5);
        assertThat(service.deleteBatch(NOW, 5)).isEqualTo(2);
        assertThat(service.deleteBatch(NOW, 5)).isZero();
    }
}
