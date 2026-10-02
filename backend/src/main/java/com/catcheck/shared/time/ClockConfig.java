package com.catcheck.shared.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Bean {@link Clock} dùng UTC cho toàn hệ thống. KHÔNG BAO GIỜ gọi
 * {@code Instant.now()}/{@code LocalDateTime.now()} trực tiếp ở nơi khác — luôn inject Clock
 * này qua constructor (xem ArchUnit R13 ở {@code architecture/TimeRuleTests}).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
