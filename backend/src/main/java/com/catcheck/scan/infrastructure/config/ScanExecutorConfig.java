package com.catcheck.scan.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bể luồng giới hạn cho pipeline scan (p6 §6.4.1): {@code core = max = 2×vCPU}, hàng đợi 50, đầy
 * thì ném {@link java.util.concurrent.RejectedExecutionException} (ánh xạ về
 * {@code 429 SCAN_BUSY} ở controller) thay vì giữ request thread hoặc âm thầm mở thêm luồng.
 *
 * <p>Bảo vệ Tomcat request thread khỏi bị giữ trong lúc chạy OpenCV (native, CPU-bound) — request
 * thread nộp việc rồi trả về ngay, controller trả {@code CompletableFuture}.</p>
 */
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
