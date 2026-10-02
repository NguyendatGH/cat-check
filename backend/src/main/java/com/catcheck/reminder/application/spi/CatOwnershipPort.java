package com.catcheck.reminder.application.spi;

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
}
