package com.catcheck.notification.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ngưỡng an toàn 20% của {@code CleanupDeadPushTokensJob} (p12 §12.6.1 quy tắc 6, p15
 * REQ-RET-02) và hai thời hạn của p12 §12.3.9.
 *
 * <p>Ngưỡng là thứ đáng test nhất ở đây: nó chỉ chạy đúng <b>một lần duy nhất</b> — lần mà một
 * bug ở điều kiện thời gian định xoá cả bảng. Nếu nó sai thì không có lần thứ hai để sửa.</p>
 */
class DeadPushTokenCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T08:00:00Z");

    private final NotificationTestDoubles.FakeSubscriptions repository =
            new NotificationTestDoubles.FakeSubscriptions();
    private final DeadPushTokenCleanupService service =
            new DeadPushTokenCleanupService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void retentionWindowsMatchPart12() {
        // p12 §12.3.9 + retention D16 ở p15 §15.5.1 — con số duy nhất trong SPEC.
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
        // 0/0 chia cho 0; và "khong co gi de xoa" phai la SUCCESS lang le, khong phai canh bao.
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
