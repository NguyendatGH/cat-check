package com.catcheck.reminder.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tập con RFC 5545 RRULE mà backend tự tính được {@code nextRunAt} — không cần thư viện ngoài
 * ({@code research-integrations.md} chưa pin thư viện RFC 5545 nào).
 *
 * <p>Chấp nhận đúng: {@code FREQ=DAILY|WEEKLY} (bắt buộc), {@code INTERVAL}, {@code BYDAY}
 * (MO..SU, không kèm số thứ tự), {@code BYHOUR}, {@code BYMINUTE}; tiền tố {@code RRULE:} tuỳ
 * chọn. Mọi key khác ({@code COUNT}, {@code UNTIL}, {@code BYMONTHDAY}, {@code WKST}…) bị từ chối
 * để không có lịch nào được lưu mà scheduler không bao giờ chạy. Đủ cho lịch gợi ý onboarding
 * kiểu "Thứ Tư &amp; Chủ Nhật" ({@code FREQ=WEEKLY;BYDAY=WE,SU}).</p>
 *
 * <p>Không có DTSTART: mốc neo cho {@code INTERVAL &gt; 1} là ngày tạo lịch (theo múi giờ của
 * lịch); giờ mặc định là {@code preferredTimeStart} hoặc 09:00. Tuần bắt đầu thứ Hai.</p>
 */
public record RecurrenceRule(
        Frequency frequency,
        int interval,
        Set<DayOfWeek> byDay,
        List<Integer> byHour,
        List<Integer> byMinute) {

    /** Tần suất được hỗ trợ. */
    public enum Frequency { DAILY, WEEKLY }

    private static final int MAX_DAILY_INTERVAL = 90;
    private static final int MAX_WEEKLY_INTERVAL = 13;
    private static final LocalTime DEFAULT_TIME = LocalTime.of(9, 0);

    /**
     * @throws IllegalArgumentException khi chuỗi ngoài tập con hỗ trợ — message chỉ là tên key,
     *         không chứa dữ liệu người dùng
     */
    public static RecurrenceRule parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty");
        }
        String body = raw.trim().toUpperCase(Locale.ROOT);
        if (body.startsWith("RRULE:")) {
            body = body.substring("RRULE:".length());
        }
        Frequency frequency = null;
        Integer interval = null;
        Set<DayOfWeek> byDay = null;
        List<Integer> byHour = null;
        List<Integer> byMinute = null;
        Set<String> seen = new HashSet<>();
        for (String part : body.split(";", -1)) {
            int eq = part.indexOf('=');
            if (eq <= 0 || eq == part.length() - 1) {
                throw new IllegalArgumentException("syntax");
            }
            String key = part.substring(0, eq);
            String value = part.substring(eq + 1);
            if (!seen.add(key)) {
                throw new IllegalArgumentException("duplicate");
            }
            switch (key) {
                case "FREQ" -> frequency = parseFrequency(value);
                case "INTERVAL" -> interval = parseInt(value, 1, Integer.MAX_VALUE);
                case "BYDAY" -> byDay = parseDays(value);
                case "BYHOUR" -> byHour = parseIntList(value, 0, 23);
                case "BYMINUTE" -> byMinute = parseIntList(value, 0, 59);
                default -> throw new IllegalArgumentException("unsupported");
            }
        }
        if (frequency == null) {
            throw new IllegalArgumentException("FREQ");
        }
        int resolvedInterval = interval == null ? 1 : interval;
        int max = frequency == Frequency.DAILY ? MAX_DAILY_INTERVAL : MAX_WEEKLY_INTERVAL;
        if (resolvedInterval > max) {
            throw new IllegalArgumentException("INTERVAL");
        }
        return new RecurrenceRule(frequency, resolvedInterval,
                byDay == null ? Set.of() : Set.copyOf(byDay),
                byHour == null ? List.of() : byHour,
                byMinute == null ? List.of() : byMinute);
    }

    /**
     * Lần xuất hiện đầu tiên SAU {@code from} (không bằng).
     *
     * @param anchor    mốc neo của chu kỳ {@code INTERVAL} — thường là {@code createdAt}
     * @param baseTime  giờ mặc định khi không có BYHOUR/BYMINUTE; {@code null} = 09:00
     */
    public Instant nextAfter(Instant from, Instant anchor, ZoneId zone, LocalTime baseTime) {
        LocalTime base = baseTime != null ? baseTime : DEFAULT_TIME;
        List<LocalTime> times = times(base);
        LocalDate anchorDate = (anchor != null ? anchor : from).atZone(zone).toLocalDate();
        Set<DayOfWeek> days = !byDay.isEmpty() || frequency == Frequency.DAILY
                ? byDay
                : EnumSet.of(anchorDate.getDayOfWeek());
        LocalDate anchorWeek = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        LocalDate day = from.atZone(zone).toLocalDate();
        // Chu kỳ dài nhất: 13 tuần hoặc 90 ngày — 2 năm là dư để luôn gặp một lần xuất hiện.
        for (int i = 0; i < 2 * 366; i++, day = day.plusDays(1)) {
            if (!matches(day, days, anchorDate, anchorWeek)) {
                continue;
            }
            for (LocalTime time : times) {
                ZonedDateTime candidate = day.atTime(time).atZone(zone);
                if (candidate.toInstant().isAfter(from)) {
                    return candidate.toInstant();
                }
            }
        }
        throw new IllegalStateException("no occurrence");
    }

    private boolean matches(LocalDate day, Set<DayOfWeek> days, LocalDate anchorDate, LocalDate anchorWeek) {
        if (!days.isEmpty() && !days.contains(day.getDayOfWeek())) {
            return false;
        }
        if (interval == 1) {
            return true;
        }
        long periods = frequency == Frequency.DAILY
                ? ChronoUnit.DAYS.between(anchorDate, day)
                : ChronoUnit.WEEKS.between(anchorWeek, day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        return Math.floorMod(periods, interval) == 0;
    }

    private List<LocalTime> times(LocalTime base) {
        List<Integer> hours = byHour.isEmpty() ? List.of(base.getHour()) : byHour;
        List<Integer> minutes = byMinute.isEmpty() ? List.of(base.getMinute()) : byMinute;
        TreeSet<LocalTime> result = new TreeSet<>();
        for (int hour : hours) {
            for (int minute : minutes) {
                result.add(LocalTime.of(hour, minute));
            }
        }
        return List.copyOf(result);
    }

    private static Frequency parseFrequency(String value) {
        return switch (value) {
            case "DAILY" -> Frequency.DAILY;
            case "WEEKLY" -> Frequency.WEEKLY;
            default -> throw new IllegalArgumentException("FREQ");
        };
    }

    private static Set<DayOfWeek> parseDays(String value) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String token : value.split(",", -1)) {
            days.add(switch (token) {
                case "MO" -> DayOfWeek.MONDAY;
                case "TU" -> DayOfWeek.TUESDAY;
                case "WE" -> DayOfWeek.WEDNESDAY;
                case "TH" -> DayOfWeek.THURSDAY;
                case "FR" -> DayOfWeek.FRIDAY;
                case "SA" -> DayOfWeek.SATURDAY;
                case "SU" -> DayOfWeek.SUNDAY;
                default -> throw new IllegalArgumentException("BYDAY");
            });
        }
        return days;
    }

    private static List<Integer> parseIntList(String value, int min, int max) {
        List<Integer> result = new ArrayList<>();
        for (String token : value.split(",", -1)) {
            result.add(parseInt(token, min, max));
        }
        return List.copyOf(result);
    }

    private static int parseInt(String value, int min, int max) {
        if (value.isEmpty() || value.length() > 4 || !value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("number");
        }
        int parsed = Integer.parseInt(value);
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException("range");
        }
        return parsed;
    }
}
