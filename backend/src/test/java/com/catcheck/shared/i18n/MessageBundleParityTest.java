package com.catcheck.shared.i18n;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mỗi {@code messages/<bundle>_en.properties} phải có đúng cùng tập khoá với {@code <bundle>_vi.properties}:
 * thiếu một bên thì {@code setUseCodeAsDefaultMessage(true)} trả nguyên tên khoá cho người dùng ngôn ngữ đó.
 */
class MessageBundleParityTest {

    /**
     * Khoá chỉ có ở bản vi, có chủ đích: mẫu khẩn cấp do thú y duyệt, AiChatService ghim EMERGENCY_LOCALE=vi,
     * và không được dịch máy (xem header ai_en.properties).
     */
    private static final Map<String, Set<String>> VI_ONLY = Map.of(
            "ai", Set.of("ai.emergency.body", "ai.emergency.lead", "ai.emergency.viewDetail"));

    @Test
    void everyBundleHasTheSameKeysInVietnameseAndEnglish() throws IOException {
        Resource[] englishBundles = new PathMatchingResourcePatternResolver()
                .getResources("classpath:messages/*_en.properties");
        assertThat(englishBundles).isNotEmpty();

        List<String> mismatches = new ArrayList<>();
        for (Resource english : englishBundles) {
            String bundle = english.getFilename().replace("_en.properties", "");
            ClassPathResource vietnamese = new ClassPathResource("messages/" + bundle + "_vi.properties");
            if (!vietnamese.exists()) {
                mismatches.add(bundle + ": missing " + vietnamese.getFilename());
                continue;
            }
            Set<String> englishKeys = keys(english);
            Set<String> vietnameseKeys = keys(vietnamese);
            Set<String> englishOnly = new TreeSet<>(englishKeys);
            englishOnly.removeAll(vietnameseKeys);
            Set<String> vietnameseOnly = new TreeSet<>(vietnameseKeys);
            vietnameseOnly.removeAll(englishKeys);
            vietnameseOnly.removeAll(VI_ONLY.getOrDefault(bundle, Set.of()));
            englishOnly.forEach(key -> mismatches.add(bundle + ": en-only " + key));
            vietnameseOnly.forEach(key -> mismatches.add(bundle + ": vi-only " + key));
        }

        assertThat(mismatches).isEmpty();
    }

    private static Set<String> keys(Resource bundle) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(bundle.getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties.stringPropertyNames();
    }
}
