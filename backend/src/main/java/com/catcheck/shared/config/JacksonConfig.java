package com.catcheck.shared.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.TimeZone;

/**
 * Cấu hình Jackson cho toàn hệ thống (Jackson 3.1.5, package gốc là {@code tools.jackson.*} —
 * đã đổi từ {@code com.fasterxml.jackson.*} kể từ Jackson 3; annotations như
 * {@code @JsonProperty} vẫn ở {@code com.fasterxml.jackson.annotation}, không đổi). Đã xác
 * nhận trực tiếp trên artifact thật (giải nén jackson-databind:3.1.5 và
 * spring-boot-jackson:4.1.1 từ Maven Central) rằng Spring Boot 4.1 tuỳ biến
 * {@code JsonMapper} qua bean {@link JsonMapperBuilderCustomizer}
 * (package {@code org.springframework.boot.jackson.autoconfigure}, module riêng
 * {@code spring-boot-jackson} — KHÔNG còn nằm trong spring-boot-autoconfigure như Boot 3).
 *
 * <p>Phần lớn tuỳ biến (time-zone UTC, không serialize ngày theo timestamp số...) đã khai báo
 * qua {@code spring.jackson.*} trong application.yml — đủ dùng ở M0. Bean dưới đây chỉ ghi rõ
 * lại time-zone UTC bằng code làm điểm neo/ví dụ cho các customizer thật sự (serializer riêng
 * cho kiểu dữ liệu nghiệp vụ...) mà các module sẽ thêm từ M1+.</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer catCheckJsonMapperBuilderCustomizer() {
        return (JsonMapper.Builder builder) -> builder.defaultTimeZone(TimeZone.getTimeZone("UTC"));
    }
}
