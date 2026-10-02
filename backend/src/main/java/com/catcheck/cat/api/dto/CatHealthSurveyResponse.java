package com.catcheck.cat.api.dto;

import com.catcheck.cat.domain.CatHealthSurvey;

import java.time.Instant;
import java.util.Map;

/** D14/D15 — trả về bản khảo sát. */
public record CatHealthSurveyResponse(
        String id,
        String catId,
        String questionnaireVersion,
        Map<String, Object> answers,
        boolean skipped,
        Instant submittedAt,
        Instant createdAt
) {

    public static CatHealthSurveyResponse from(CatHealthSurvey survey) {
        return new CatHealthSurveyResponse(
                survey.getId().toString(),
                survey.getCatId().toString(),
                survey.getQuestionnaireVersion(),
                survey.getAnswers(),
                survey.isSkipped(),
                survey.getSubmittedAt(),
                survey.getCreatedAt());
    }
}
