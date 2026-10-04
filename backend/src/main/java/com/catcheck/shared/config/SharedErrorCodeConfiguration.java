package com.catcheck.shared.config;

import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.security.AdminApiErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký các mã lỗi dùng chung của {@code shared} vào {@code ErrorCodeRegistry}.
 *
 * <p>Chỉ {@link AdminApiErrorCode#ACCESS_DENIED} — ba mã còn lại trùng tên với hằng đã có trong
 * {@code ColorChartErrorCode}/{@code CatErrorCode}/{@code ContentErrorCode}; xem javadoc
 * {@link AdminApiErrorCode} để biết vì sao đăng ký thêm sẽ làm app không khởi động.</p>
 *
 * <p>Mỗi hằng là một bean riêng chứ không phải {@code @Bean List<ErrorCode>} — lý do giống hệt
 * {@code CreditErrorCodeConfiguration}.</p>
 */
@Configuration
public class SharedErrorCodeConfiguration {

    @Bean
    ErrorCode adminAccessDenied() {
        return AdminApiErrorCode.ACCESS_DENIED;
    }
}
