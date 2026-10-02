package com.catcheck.export.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bể luồng riêng cho sinh PDF (p13 §13.6.1: bất đồng bộ vì tốn CPU — render HTML→PDF, nhúng
 * font, vẽ SVG qua Batik). Nhỏ hơn {@code scanExecutor}: PDF hiếm khi đồng thời nhiều (trần 1
 * job/user ở DB, p4 G1).
 */
@Configuration
public class ExportExecutorConfig {

    @Bean("exportExecutor")
    public ThreadPoolTaskExecutor exportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("export-pdf-");
        executor.initialize();
        return executor;
    }
}
