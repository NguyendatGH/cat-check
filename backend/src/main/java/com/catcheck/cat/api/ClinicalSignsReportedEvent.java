package com.catcheck.cat.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * D20 — chủ nuôi vừa khai dấu hiệu lâm sàng.
 *
 * <p>Đây là đầu vào của rule {@code URGENT_CLINICAL_SIGN} (p6 §6.9.6) — rule duy nhất không phụ
 * thuộc bảng màu và có {@code cooldown_hours = 0}, nên mỗi lần khai đều là một cảnh báo riêng.</p>
 *
 * <p>Module cat KHÔNG tự sinh {@code health_flag} và KHÔNG tự gửi thông báo: đó là việc của module
 * insight/notification. Giữ ranh giới ở đây nghĩa là khi chưa có module insight, khai dấu hiệu vẫn
 * chạy và dữ liệu vẫn được ghi đầy đủ, thay vì hỏng theo.</p>
 *
 * <p>Hợp đồng response của D20 cần {@code triggeredFlag} — trả {@code null} cho tới khi có module
 * sinh cờ; xem {@code docs/handovers/A3.md}.</p>
 *
 * @param reportId      bản khai vừa tạo
 * @param catId         mèo được khai
 * @param ownerId       chủ sở hữu
 * @param signs         tập dấu hiệu đã khai
 * @param source        nơi phát sinh khai báo
 * @param blockingShown màn hình cảnh báo chặn đã hiện hay chưa
 * @param occurredAt    thời điểm phát
 */
public record ClinicalSignsReportedEvent(
        UUID reportId,
        UUID catId,
        UUID ownerId,
        Set<String> signs,
        String source,
        boolean blockingShown,
        Instant occurredAt) implements CatEvent {

    @Override
    public String eventType() {
        return "CLINICAL_SIGNS_REPORTED";
    }
}
