package com.catcheck.shared.config;

import com.catcheck.shared.job.JobProperties;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Bật {@code @Scheduled} + khoá phân tán ShedLock (bảng {@code shedlock}, migration V4.1) để
 * nhiều instance ứng dụng không chạy trùng cùng một job định kỳ (p12 §12.7).
 *
 * <p>{@code defaultLockAtMostFor = "PT10M"} chỉ là lưới an toàn cho job quên khai báo. Mọi job
 * nghiệp vụ <b>phải</b> ghi {@code lockAtMostFor} riêng theo đúng cột trong bảng ở p12 §12.6 —
 * giá trị đó là một phần đặc tả của từng job, không phải tham số tinh chỉnh.</p>
 *
 * <p>{@code @EnableConfigurationProperties(JobProperties.class)}: {@code CatCheckApplication}
 * không có {@code @ConfigurationPropertiesScan}, nên mỗi record cấu hình phải được đăng ký tường
 * minh (cùng tiền lệ với {@code shared.i18n.LocaleConfig}). Thiếu dòng này thì placeholder
 * {@code ${catcheck.jobs.*}} trong {@code @Scheduled} vẫn giải được (nó đọc trực tiếp từ
 * {@code Environment}) nhưng {@code JobRunner}/{@code JobHeartbeatCheckJob} không có bean để
 * inject và ứng dụng không khởi động — một kiểu lỗi chỉ lộ ra khi chạy context thật.</p>
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@EnableConfigurationProperties(JobProperties.class)
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
