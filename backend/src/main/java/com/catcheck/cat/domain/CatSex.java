package com.catcheck.cat.domain;

/**
 * Giới tính (p4 §4.4.4). `UNKNOWN` là mặc định: onboarding hỏi nhưng không bắt buộc,
 * và đoán giới tính từ tên mèo là điều CatCheck không làm. *
 * <p>Lưu dạng {@code VARCHAR + CHECK} (p4 §4.1.3): Hibernate ghi bằng {@code STRING}, không
 * dùng {@code @Enumerated(ORDINAL)} — đổi thứ tự hằng số sau này sẽ hỏng dữ liệu đã lưu.</p>
 */
public enum CatSex {

    UNKNOWN,
    FEMALE,
    MALE
}
