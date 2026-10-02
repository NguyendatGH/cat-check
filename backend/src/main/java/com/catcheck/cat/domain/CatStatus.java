package com.catcheck.cat.domain;

/**
 * Trạng thái hồ sơ (p4 §4.4.4).
 *
 * <p>`ARCHIVED` là "bé đã mất / đã cho đi" — KHÁC xoá: dữ liệu và biểu đồ vẫn xem được, chỉ ẩn khỏi
 * danh sách chọn khi quét. Xem p4 §4.8.2. *
 * <p>Lưu dạng {@code VARCHAR + CHECK} (p4 §4.1.3): Hibernate ghi bằng {@code STRING}, không
 * dùng {@code @Enumerated(ORDINAL)} — đổi thứ tự hằng số sau này sẽ hỏng dữ liệu đã lưu.</p>
 */
public enum CatStatus {

    ACTIVE,
    ARCHIVED
}
