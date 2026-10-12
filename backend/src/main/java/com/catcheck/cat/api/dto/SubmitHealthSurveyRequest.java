package com.catcheck.cat.api.dto;

import java.util.Map;

public record SubmitHealthSurveyRequest(
        String questionnaireVersion,
        Map<String, Object> answers,
        boolean skipped
) {
}
