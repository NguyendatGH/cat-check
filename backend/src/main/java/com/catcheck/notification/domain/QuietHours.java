package com.catcheck.notification.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Giờ im lặng của một user (p12 §12.5.3, cột ở p4 F4).
 *
 * <p>Mặc định 22:00–07:00 theo giờ địa phương của user; {@code end} là <b>phút đầu tiên được
 * phép gửi</b>. {@code start > end} là HỢP LỆ (khung vắt qua nửa đêm) — đừng "sửa" bằng cách
 * ép {@code end > start}.</p>
 *
 * @param enabled user có bật giờ im lặng không
 * @param start   mốc bắt đầu im lặng, giờ địa phương
 * @param end     mốc hết im lặng, giờ địa phương (phút đầu tiên được phép gửi)
 */
public record QuietHours(boolean enabled, LocalTime start, LocalTime end) {

    public QuietHours {
        if (start == null || end == null) {
            throw new IllegalArgumentException("quietHours.start/end không được null");
        }
    }

    public static QuietHours defaults() {
        return new QuietHours(true, LocalTime.of(22, 0), LocalTime.of(7, 0));
    }

    /** Khung rỗng ({@code start == end}) nghĩa là không có giờ im lặng nào. */
    public boolean isQuietAt(Instant moment, ZoneId zone) {
        if (!enabled || start.equals(end)) {
            return false;
        }
        LocalTime localTime = ZonedDateTime.ofInstant(moment, zone).toLocalTime();
        if (start.isBefore(end)) {
            return !localTime.isBefore(start) && localTime.isBefore(end);
        }
        // Vắt qua nửa đêm: 22:00 -> 07:00.
        return !localTime.isBefore(start) || localTime.isBefore(end);
    }

    /**
     * Mốc UTC sớm nhất được phép gửi nếu {@code moment} đang rơi vào giờ im lặng — tức
     * {@code quiet_hours_end} của hôm nay nếu chưa qua, của ngày kế tiếp nếu đã qua
     * (p12 §12.5.3: "dời sang {@code quiet_hours_end}").
     */
    public Instant nextAllowedAfter(Instant moment, ZoneId zone) {
        if (!isQuietAt(moment, zone)) {
            return moment;
        }
        ZonedDateTime local = ZonedDateTime.ofInstant(moment, zone);
        ZonedDateTime candidate = local.toLocalDate().atTime(end).atZone(zone);
        if (!candidate.toInstant().isAfter(moment)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toInstant();
    }
}
