package com.catcheck.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
