package com.catcheck.credit.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

/**
 * Bean dùng chung cho module credit.
 *
 * <p>A4 không được sửa {@code CatCheckApplication} nên không thêm
 * {@code @ConfigurationPropertiesScan} ở đây; mỗi thứ tự nạp bằng annotation riêng trên chính nó.
 */
@Configuration(proxyBeanMethods = false)
public class CreditConfiguration {

    /**
     * Nguồn ngẫu nhiên cho {@link com.catcheck.credit.domain.ActivationCodeFormat}.
     *
     * <p>Phải là {@link SecureRandom}, KHÔNG phải {@code Random}: mã kích hoạt là credit thật,
     * và một bộ sinh số giả đoán trước được thì toàn bộ mã chưa bán đều bị dò trước (p5 §5.9,
     * p11 §11.7.4).</p>
     *
     * <p>Không tự khởi tạo {@code new SecureRandom()} trong service: tầng {@code application}
     * không được tự tạo phụ thuộc hạ tầng. Bean này là nơi duy nhất module tạo ra nó.</p>
     */
    @Bean
    public SecureRandom creditSecureRandom() {
        return new SecureRandom();
    }
}
