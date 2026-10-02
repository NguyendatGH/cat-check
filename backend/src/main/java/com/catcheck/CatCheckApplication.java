package com.catcheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Điểm khởi động ứng dụng CatCheck.
 *
 * <p>Ứng dụng chạy ở UTC (xem {@code shared.time.ClockConfig} + {@code application.yml}
 * {@code spring.jackson.time-zone=UTC}). Tham số JVM {@code -Duser.timezone=UTC} được truyền ở
 * entrypoint (Dockerfile / script chạy local), không đặt cứng trong mã nguồn để tránh sai lệch
 * giữa các môi trường.</p>
 */
@SpringBootApplication
public class CatCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatCheckApplication.class, args);
    }
}
