package com.catcheck.reminder.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tập con RRULE tự tính (H7). Giờ VN = UTC+7, không DST; 2026-10-03 là thứ Bảy. */
class RecurrenceRuleTest {

    private static final ZoneId SAIGON = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Thứ Bảy 03/10, 11:00 giờ VN. */
    private static final Instant SAT_11H = Instant.parse("2026-10-03T04:00:00Z");

    @Test
    void weeklyByDayPicksNextMatchingDayAtPreferredTime() {
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=WE,SU");

        // Chủ Nhật 04/10 09:00 giờ VN.
        assertThat(rule.nextAfter(SAT_11H, SAT_11H, SAIGON, LocalTime.of(9, 0)))
                .isEqualTo(Instant.parse("2026-10-04T02:00:00Z"));
    }

    @Test
    void occurrenceExactlyAtFromIsSkippedSoRecomputeAfterFireAdvances() {
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=WE,SU");
        Instant firedAt = Instant.parse("2026-10-04T02:00:00Z");

        // Thứ Tư 07/10 09:00 giờ VN.
        assertThat(rule.nextAfter(firedAt, SAT_11H, SAIGON, LocalTime.of(9, 0)))
                .isEqualTo(Instant.parse("2026-10-07T02:00:00Z"));
    }

    @Test
    void dailyWithByHourAndByMinute() {
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=DAILY;BYHOUR=8,20;BYMINUTE=30");

        // 20:30 giờ VN cùng ngày.
        assertThat(rule.nextAfter(SAT_11H, SAT_11H, SAIGON, null))
                .isEqualTo(Instant.parse("2026-10-03T13:30:00Z"));
    }

    @Test
    void weeklyIntervalIsAnchoredOnCreationWeek() {
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=2");
        Instant createdMonday = Instant.parse("2026-10-05T00:00:00Z"); // thứ Hai 07:00 giờ VN
        Instant mondayAfterNine = Instant.parse("2026-10-05T03:00:00Z");

        // Thứ Hai tuần này đã qua 09:00 ⇒ thứ Hai cách 2 tuần (19/10), không phải 12/10.
        assertThat(rule.nextAfter(mondayAfterNine, createdMonday, SAIGON, null))
                .isEqualTo(Instant.parse("2026-10-19T02:00:00Z"));
    }

    @Test
    void usesTheReminderTimezone() {
        RecurrenceRule rule = RecurrenceRule.parse("FREQ=DAILY");

        assertThat(rule.nextAfter(SAT_11H, SAT_11H, ZoneOffset.UTC, LocalTime.of(9, 0)))
                .isEqualTo(Instant.parse("2026-10-03T09:00:00Z")); // 04:00Z còn trước 09:00 UTC cùng ngày
        assertThat(rule.nextAfter(SAT_11H, SAT_11H, SAIGON, LocalTime.of(9, 0)))
                .isEqualTo(Instant.parse("2026-10-04T02:00:00Z"));
    }

    @Test
    void acceptsPrefixAndLowercase() {
        assertThat(RecurrenceRule.parse("rrule:freq=weekly;byday=mo,th").byDay()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "FREQ=MONTHLY", "FREQ=WEEKLY;COUNT=3", "FREQ=WEEKLY;UNTIL=20261231T000000Z",
            "FREQ=WEEKLY;BYDAY=1MO", "BYDAY=MO", "FREQ=DAILY;INTERVAL=0", "FREQ=DAILY;INTERVAL=91",
            "FREQ=WEEKLY;INTERVAL=14", "FREQ=DAILY;BYHOUR=24", "FREQ=DAILY;;", "FREQ=DAILY;FREQ=WEEKLY",
            "garbage"})
    void rejectsAnythingOutsideTheSupportedSubset(String raw) {
        assertThatThrownBy(() -> RecurrenceRule.parse(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nextRunCalculatorSchedulesRruleReminders() {
        Reminder reminder = new Reminder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                ReminderType.SCAN_ROUTINE, ScheduleKind.RRULE, null, "FREQ=WEEKLY;BYDAY=WE,SU",
                LocalTime.of(9, 0), null, "Asia/Ho_Chi_Minh", null, null, null, List.of("PUSH"),
                ReminderSource.USER, true, SAT_11H, SAT_11H);

        assertThat(NextRunCalculator.nextRun(reminder, SAT_11H))
                .isEqualTo(Instant.parse("2026-10-04T02:00:00Z"));
    }
}
