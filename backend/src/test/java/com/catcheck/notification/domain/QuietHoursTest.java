package com.catcheck.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(quietHours.isQuietAt(Instant.parse("2026-10-03T15:00:00Z"), SAIGON)).isTrue();
        assertThat(quietHours.isQuietAt(Instant.parse("2026-10-03T19:00:00Z"), SAIGON)).isTrue();
        assertThat(quietHours.isQuietAt(Instant.parse("2026-10-03T00:00:00Z"), SAIGON)).isFalse();
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
        assertThat(QuietHours.defaults()
                .nextAllowedAfter(Instant.parse("2026-10-03T15:30:00Z"), SAIGON))
                .isEqualTo(Instant.parse("2026-10-04T00:00:00Z"));
    }

    @Test
    void deferralAfterMidnightLandsOnTheSameMorning() {
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
