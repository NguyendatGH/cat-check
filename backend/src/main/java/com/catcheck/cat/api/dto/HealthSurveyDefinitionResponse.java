package com.catcheck.cat.api.dto;

import java.util.List;

/**
 * F6 — {@code GET /reference/health-survey/{version}} (p8 §8.4.6).
 *
 * <p>Nguồn dữ liệu là file JSON đi kèm build ({@code resources/questionnaire/health-survey-*.json}),
 * KHÔNG phải bảng DB — đúng như p4 C3 chốt: "Định nghĩa câu hỏi (text, option, thứ tự) nằm trong
 * file JSON đi kèm build hoặc trong {@code app_setting}, khoá theo {@code questionnaire_version}".</p>
 *
 * <p>Trả {@code labelKey} (khoá i18n) chứ không trả câu chữ: cùng nguyên tắc với
 * {@code monitoring_rule.message_key} (quyết định #15) và tránh việc câu hỏi tồn tại hai bản
 * (một trong i18n của client, một trong response) rồi lệch nhau. Khoá khớp đúng cây i18n
 * {@code onboarding.json} client đang dùng.</p>
 */
public record HealthSurveyDefinitionResponse(String version, List<Question> questions) {

    /**
     * @param type {@code SINGLE} chọn một, {@code MULTI} chọn nhiều
     */
    public record Question(
            String key,
            String type,
            boolean required,
            int sortOrder,
            String labelKey,
            List<Option> options
    ) {
    }

    public record Option(String code, String labelKey) {
    }
}
