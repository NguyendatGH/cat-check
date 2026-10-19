package com.catcheck.cat.api.dto;

import java.util.List;

public record HealthSurveyDefinitionResponse(String version, List<Question> questions) {

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
