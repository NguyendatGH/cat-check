package com.catcheck.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình springdoc-openapi (chạy ở {@code /v3/api-docs} và {@code /swagger-ui.html}, tự động
 * nhờ {@code springdoc-openapi-starter-webmvc-ui} có mặt trên classpath). Bean dưới đây chỉ
 * điền metadata mô tả API, không tắt/bật tính năng gì thêm ở M0.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI catCheckOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CatCheck API")
                        .description("API theo dõi sức khoẻ mèo qua cát vệ sinh đổi màu theo pH.")
                        .version("v1")
                        .license(new License().name("Proprietary")));
    }
}
