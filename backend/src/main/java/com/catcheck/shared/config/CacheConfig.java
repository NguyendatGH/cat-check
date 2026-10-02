package com.catcheck.shared.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache in-memory bằng Caffeine. Ở M0 chưa có cache region nghiệp vụ nào — cấu hình mặc định
 * (TTL 10 phút, tối đa 10_000 entry/region) áp dụng chung cho mọi tên cache mà module nghiệp
 * vụ khai báo qua {@code @Cacheable} từ M1+.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(10_000));
        return cacheManager;
    }
}
