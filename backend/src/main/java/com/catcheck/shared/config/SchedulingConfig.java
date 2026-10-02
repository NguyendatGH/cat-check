package com.catcheck.shared.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Bật {@code @Scheduled} + khoá phân tán ShedLock (bảng {@code shedlock}, migration V4.1) để
 * nhiều instance ứng dụng không chạy trùng cùng một job định kỳ. Ở M0 chưa có
 * {@code @Scheduled}/{@code @SchedulerLock} thực tế nào (chưa có job nghiệp vụ) — class này chỉ
 * dựng sẵn hạ tầng để M1+ dùng ngay.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
