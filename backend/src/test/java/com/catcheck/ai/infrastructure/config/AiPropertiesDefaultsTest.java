package com.catcheck.ai.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chỉ có OPENAI_API_KEY thì LLM vẫn phải TẮT: muốn gửi câu hỏi ra nhà cung cấp phải chủ động đặt
 * CATCHECK_AI_ENABLED=true. Đọc application.yml thật, giải placeholder với môi trường rỗng.
 */
class AiPropertiesDefaultsTest {

    @Test
    void llmIsDisabledByDefaultAndCanBeEnabledByEnvironmentVariable() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();
        String raw = properties.getProperty("catcheck.ai.enabled");
        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}", ":", null, true);

        assertThat(helper.replacePlaceholders(raw, name -> null)).isEqualTo("false");
        assertThat(helper.replacePlaceholders(raw, name -> "CATCHECK_AI_ENABLED".equals(name) ? "true" : null))
                .isEqualTo("true");
    }
}
