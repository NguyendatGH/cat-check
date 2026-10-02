package com.catcheck.cat.api.dto;

import java.util.Map;

/**
 * D14 — body nộp/bỏ qua khảo sát sức khoẻ. {@code answers} truyền thẳng vào JSONB
 * {@code cat_health_survey.answers} (p4 C3) — cấu trúc theo {@code questionnaireVersion}, không
 * validate schema ở tầng Java (xem javadoc {@code CatHealthSurveyService#submit}).
 */
public record SubmitHealthSurveyRequest(
        String questionnaireVersion,
        Map<String, Object> answers,
        boolean skipped
) {
}
