package com.catcheck.privacy.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class DsarExportExecutorConfig {
    @Bean("dsarExportExecutor")
    public ThreadPoolTaskExecutor dsarExportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("dsar-export-");
        executor.initialize();
        return executor;
    }
}
