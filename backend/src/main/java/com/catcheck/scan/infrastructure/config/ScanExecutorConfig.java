package com.catcheck.scan.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ScanExecutorConfig {

    @Bean("scanExecutor")
    public ThreadPoolTaskExecutor scanExecutor() {
        int vcpu = Math.max(1, Runtime.getRuntime().availableProcessors());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2 * vcpu);
        executor.setMaxPoolSize(2 * vcpu);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("scan-pipeline-");
        RejectedExecutionHandler abortPolicy = new ThreadPoolExecutor.AbortPolicy();
        executor.setRejectedExecutionHandler(abortPolicy);
        executor.initialize();
        return executor;
    }
}
