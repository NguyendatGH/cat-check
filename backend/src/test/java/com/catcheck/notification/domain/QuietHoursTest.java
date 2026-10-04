package com.catcheck.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Giờ im lặng 22:00–07:00 giờ địa phương (p12 §12.5.3, p4 F4).
 *
 * <p>Khung VẮT QUA NỬA ĐÊM ({@code start > end}) là trường hợp mặc định chứ không phải ngoại lệ
 * — p4 F4 ghi rõ. Giờ VN là UTC+7 và không có DST nên mốc UTC quy đổi được bằng tay.</p>
 */
class QuietHoursTest {

    private static final ZoneId SAIGON = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    void defaultsMatchPart4() {
        QuietHours defaults = QuietHours.defaults();
        assertThat(defaults.enabled()).isTrue();
        assertThat(defaults.start()).isEqualTo(LocalTime.of(22, 0));
        assertThat(defaults.end()).isEqualTo(LocalTime.of(7, 0));
    }

    @Test
    void windowCrossingMidnightCoversBothSidesOfIt() {
        QuietHours quietHours = QuietHours.defaults();
        // 22:00 giờ VN — phút đầu tiên im lặng.
        assertThat(quietHours.isQuietAt(Instant.parse("2026-10-03T15:00:00Z"), SAIGON)).isTrue();
        // 02:00 giờ VN hôm sau.
        assertThat(quietHours.isQuietAt(Instant.parse("2026-10-03T19:00:00Z"), SAIGON)).isTrue();
        // 07:00 giờ VN — phút đầu tiên ĐƯỢC PHÉP gửi, không còn im lặng.
        assertThat(quietHours.isQuietAt(Instant.parse("2026-10-03T00:00:00Z"), SAIGON)).isFalse();
        // 17:00 giờ VN.
        assertThat(quietHours.isQuietAt(Instant.parse("2026-10-03T10:00:00Z"), SAIGON)).isFalse();
    }

    @Test
    void disabledWindowIsNeverQuiet() {
        QuietHours quietHours = new QuietHours(false, LocalTime.of(22, 0), LocalTime.of(7, 0));
        assertThat(quietHours.isQuietAt(Instant.parse("2026-10-03T19:00:00Z"), SAIGON)).isFalse();
    }

    @Test
    void sameStartAndEndMeansNoQuietWindow() {
        QuietHours quietHours = new QuietHours(true, LocalTime.of(22, 0), LocalTime.of(22, 0));
        assertThat(quietHours.isQuietAt(Instant.parse("2026-10-03T19:00:00Z"), SAIGON)).isFalse();
    }

    @Test
    void nonCrossingWindowIsHalfOpen() {
        QuietHours quietHours = new QuietHours(true, LocalTime.of(13, 0), LocalTime.of(14, 0));
        assertThat(quietHours.isQuietAt(Instant.parse("2026-10-03T06:00:00Z"), SAIGON)).isTrue();
        assertThat(quietHours.isQuietAt(Instant.parse("2026-10-03T07:00:00Z"), SAIGON)).isFalse();
    }

    @Test
    void deferralBeforeMidnightLandsOnNextMorning() {
        // 22:30 giờ VN ngày 03/10 ⇒ 07:00 giờ VN ngày 04/10 = 00:00Z ngày 04/10.
        assertThat(QuietHours.defaults()
                .nextAllowedAfter(Instant.parse("2026-10-03T15:30:00Z"), SAIGON))
                .isEqualTo(Instant.parse("2026-10-04T00:00:00Z"));
    }

    @Test
    void deferralAfterMidnightLandsOnTheSameMorning() {
        // 02:00 giờ VN ngày 04/10 ⇒ 07:00 cùng ngày = 00:00Z ngày 04/10.
        assertThat(QuietHours.defaults()
                .nextAllowedAfter(Instant.parse("2026-10-03T19:00:00Z"), SAIGON))
                .isEqualTo(Instant.parse("2026-10-04T00:00:00Z"));
    }

    @Test
    void outsideTheWindowTheMomentIsReturnedUnchanged() {
        Instant daytime = Instant.parse("2026-10-03T10:00:00Z");
        assertThat(QuietHours.defaults().nextAllowedAfter(daytime, SAIGON)).isEqualTo(daytime);
    }
}
