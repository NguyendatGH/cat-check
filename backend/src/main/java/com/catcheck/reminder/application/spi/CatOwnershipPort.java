package com.catcheck.reminder.application.spi;

import java.util.Optional;
import java.util.UUID;

/**
 * Kiểm tra một mèo có thuộc chủ nuôi đang đăng nhập và còn sống (chưa soft-delete) không.
 *
 * <p>Đọc thẳng bảng {@code cat} bằng JDBC read-only, KHÔNG import type Java nào của
 * {@code com.catcheck.cat.*} — cùng judgment call mà {@code export} và {@code scan} đã dùng
 * (xem javadoc {@code export/package-info.java}). Nhờ vậy {@code reminder} chỉ cần
 * {@code allowedDependencies = {"shared"}} và không tạo thêm cạnh phụ thuộc giữa hai module.</p>
 */
public interface CatOwnershipPort {

    boolean isOwnedAndAlive(UUID catId, UUID ownerId);

    /**
     * Tên bé mèo, để {@code SendDueRemindersJob} render nội dung template
     * {@code REMINDER_SCAN_DUE}/{@code REMINDER_OVERDUE} ("Đến lịch quét cho {tên mèo} hôm
     * nay." — p12 §12.2.2).
     *
     * @return rỗng khi mèo đã bị xoá mềm hoặc không còn thuộc chủ này. Tên mèo là dữ liệu cá
     *         nhân của chủ nuôi (p15 §15.2.3) nên <b>không bao giờ</b> ghi vào log — chỉ đi
     *         vào {@code notification.body_snapshot}
     */
    Optional<String> findAliveCatName(UUID catId, UUID ownerId);
}
