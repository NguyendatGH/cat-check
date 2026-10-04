package com.catcheck.ai.infrastructure.config;

import com.catcheck.ai.application.AiRuntimeConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean
    AiRuntimeConfig aiRuntimeConfig(AiProperties properties) {
        return new AiRuntimeConfig(properties.maxContextChunks(), properties.maxHistoryMessages());
    }
}
