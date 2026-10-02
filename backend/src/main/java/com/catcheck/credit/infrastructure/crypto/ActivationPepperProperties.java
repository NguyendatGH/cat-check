package com.catcheck.credit.infrastructure.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Pepper dùng để HMAC mã kích hoạt — đọc thẳng từ biến môi trường (p18 §18.5, p11 §11.7.4).
 *
 * <p><b>Vì sao không dùng {@code @ConfigurationProperties}:</b> biến môi trường bắt buộc có tên
 * CHÍNH XÁC {@code ACTIVATION_PEPPER} và {@code ACTIVATION_PEPPER_VERSION} (p18 §18.5 đã liệt
 * kê đúng hai tên này, và p11 §11.7.4 yêu cầu tên đó). Relaxed binding của
 * {@code @ConfigurationProperties} sẽ sinh tên {@code CATCHECK_ACTIVATION_PEPPER} cho prefix
 * {@code catcheck.*}, tức là tên khác. Đọc bằng {@code ${ACTIVATION_PEPPER}} giữ đúng tên p18
 * khai, mà vẫn cho phép override bằng biến hệ thống / test.</p>
 *
 * <p>Không có {@code @DefaultValue} cho pepper: thiếu biến thì phải FAIL FAST lúc khởi động
 * chứ không được âm thầm rơi về một giá trị rỗng yếu — xem kiểm tra độ dài trong
 * {@link HmacActivationCodeHasher}.</p>
 *
 * @param pepper  khoá HMAC, ≥ 32 byte, lưu NGOÀI DB. Xoay bằng cách đổi biến + tăng version.
 * @param version version pepper hiện hành, ghi vào {@code activation_code.pepper_version}
 */
@Component
public record ActivationPepperProperties(
        @Value("${ACTIVATION_PEPPER:}") String pepper,
        @Value("${ACTIVATION_PEPPER_VERSION:1}") int version
) {

    public ActivationPepperProperties {
        if (pepper == null) {
            pepper = "";
        }
        if (version < 1) {
            throw new IllegalStateException(
                    "ACTIVATION_PEPPER_VERSION phải >= 1, nhận: " + version);
        }
    }

    public short pepperVersion() {
        return (short) version;
    }
}
