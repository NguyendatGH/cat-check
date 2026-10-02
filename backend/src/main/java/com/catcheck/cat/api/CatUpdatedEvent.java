package com.catcheck.cat.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * D4/D6/D7/D8/D10/D11 — hồ sơ mèo thay đổi.
 *
 * <p>Gộp mọi thay đổi thành MỘT sự kiện kèm {@link #changedFields()}, thay vì sáu loại sự kiện riêng:
 * người tiêu thụ (insight tính lại mức nền, audit ghi nhật ký) quan tâm "hồ sơ này vừa đổi gì" chứ
 * không quan tâm endpoint nào đã gọi. Tách nhỏ sẽ tạo ra sáu chỗ phải cập nhật cùng lúc mỗi khi
 * thêm một trường vào hồ sơ.</p>
 *
 * @param catId         hồ sơ bị thay đổi
 * @param ownerId       chủ sở hữu
 * @param changedFields tên các trường thực sự đổi, dùng để người tiêu thụ quyết định có cần tính lại
 *                      hay không
 * @param previousStatus trạng thái trước khi đổi; {@code null} nghĩa là không phải thao tác chuyển
 *                       trạng thái
 * @param occurredAt    thời điểm phát
 */
public record CatUpdatedEvent(
        UUID catId,
        UUID ownerId,
        Set<String> changedFields,
        String previousStatus,
        Instant occurredAt) implements CatEvent {

    @Override
    public String eventType() {
        return "CAT_UPDATED";
    }
}
