package com.catcheck.scan.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bể luồng của backfill bảng màu (L33/L35, p6 §6.5.4).
 *
 * <p><b>Một luồng duy nhất, hàng đợi 4.</b> Hai lượt backfill chạy song song trên cùng tập
 * {@code scan_analysis} sẽ tranh nhau cột {@code is_current} và cái thua ném lỗi unique giữa lô.
 * Nối tiếp là ràng buộc nghiệp vụ, không phải lựa chọn hiệu năng — và hàng đợi nhỏ để admin bấm
 * nhầm mười lần không xếp mười lượt chạy.</p>
 *
 * <p>Tách khỏi {@code scanExecutor} vì hai loại việc có hình dạng khác nhau: pipeline scan là
 * CPU-bound vài trăm millisecond cho một request đang chờ, còn backfill là I/O-bound hàng phút
 * và không ai chờ. Dùng chung bể thì một lượt backfill chiếm hết luồng và mọi lần quét của người
 * dùng trả {@code 429}.</p>
 */
@Configuration
public class ChartBackfillExecutorConfig {

    @Bean("chartBackfillExecutor")
    public ThreadPoolTaskExecutor chartBackfillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix("chart-backfill-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
