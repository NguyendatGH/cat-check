package com.catcheck.cat.domain;

/**
 * Nơi lưu ảnh đại diện của mèo (p4 C1).
 *
 * <p>Chỉ lưu TÊN provider, không lưu URL: module `media` sở hữu cách đổi provider sau này, và đổi
 * từ LOCAL sang CLOUDINARY không được làm hỏng dữ liệu đã lưu (p4 §4.3 K5: không bảng `stored_file`). *
 * <p>Lưu dạng {@code VARCHAR + CHECK} (p4 §4.1.3): Hibernate ghi bằng {@code STRING}, không
 * dùng {@code @Enumerated(ORDINAL)} — đổi thứ tự hằng số sau này sẽ hỏng dữ liệu đã lưu.</p>
 */
public enum AvatarStorageProvider {

    LOCAL,
    CLOUDINARY
}
