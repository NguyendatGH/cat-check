package com.catcheck.shared.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer catCheckJsonMapperBuilderCustomizer() {
        return (JsonMapper.Builder builder) -> builder.defaultTimeZone(TimeZone.getTimeZone("UTC"));
    }
}
