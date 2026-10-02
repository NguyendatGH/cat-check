package com.catcheck.privacy.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * Tính ngày làm việc cho SLA DSAR (p15 REQ-DSAR-02): loại trừ Thứ Bảy, Chủ nhật và ngày
 * lễ {@code OFFICIAL}. Nghỉ {@code COMPANY} <b>không</b> được dùng để kéo dài SLA luật định
 * (p4 B8).
 *
 * <p>Thiếu dữ liệu ngày lễ của năm kế tiếp trước 31/12 là toàn bộ SLA DSAR tính sai từ
 * 01/01 — hàm ném lỗi thay vì âm thầm coi mọi ngày là ngày làm việc (p4 B8).</p>
 */
public final class BusinessDays {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private BusinessDays() {
    }

    /**
     * Cộng {@code days} ngày làm việc vào {@code from} (UTC) — giữ nguyên giờ:phút của
     * {@code from}, chỉ dịch chuyển NGÀY. Dùng tính {@code ack_due_at} = received_at + 2
     * ngày làm việc (p4 B5).
     *
     * @param from              mốc bắt đầu
     * @param days              số ngày làm việc cần cộng, &gt; 0
     * @param officialHolidays tập ngày lễ OFFICIAL (COMPANY không tính)
     * @param today             "hôm nay" — khi vượt quá năn kế tiếp mà holidays chưa có dữ
     *                          liệu năm đó ⇒ ném {@link IllegalStateException}
     * @return mốc cùng giờ:phút của {@code from} nhưng ở ngày làm việc đích
     */
    public static Instant addBusinessDays(Instant from, int days, Set<LocalDate> officialHolidays, Instant today) {
        if (days <= 0) {
            throw new IllegalArgumentException("Số ngày làm việc phải > 0: " + days);
        }
        if (officialHolidays == null || officialHolidays.isEmpty()) {
            throw new IllegalStateException(
                    "holiday_calendar chưa có dữ liệu ngày lễ OFFICIAL — không tính được SLA (p4 B8)");
        }
        LocalDate horizon = today.atZone(UTC).toLocalDate().plusYears(1);
        LocalDate date = from.atZone(UTC).toLocalDate();
        int added = 0;
        while (added < days) {
            date = date.plusDays(1);
            if (date.isAfter(horizon)) {
                throw new IllegalStateException(
                        "holiday_calendar chưa có đủ dữ liệu ngày lễ để tính SLA (p4 B8) — "
                                + "yêu cầu DPO nạp ngày lễ của năm kế tiếp trước 31/12");
            }
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !officialHolidays.contains(date)) {
                added++;
            }
        }
        return date.atTime(from.atZone(UTC).toLocalTime()).toInstant(UTC);
    }
}
