package com.catcheck.cat.domain;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cat_health_survey")
public class CatHealthSurvey {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "cat_id", nullable = false)
    private UUID catId;

    @Column(name = "questionnaire_version", nullable = false, length = 16)
    private String questionnaireVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> answers = Map.of();

    @Column(name = "skipped", nullable = false)
    private boolean skipped;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CatHealthSurvey() {
    }

    private CatHealthSurvey(UUID id, UUID catId, String questionnaireVersion, boolean skipped,
                            Map<String, Object> answers, UUID submittedBy, Instant now) {
        this.id = Objects.requireNonNull(id, "cat_health_survey.id phải có giá trị");
        this.catId = Objects.requireNonNull(catId, "cat_health_survey.catId phải có giá trị");
        this.questionnaireVersion = Objects.requireNonNull(
                questionnaireVersion, "cat_health_survey.questionnaireVersion phải có giá trị");
        this.skipped = skipped;
        this.answers = Map.copyOf(answers);
        this.submittedBy = submittedBy;
        this.submittedAt = skipped ? null : now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static CatHealthSurvey submitted(UUID id, UUID catId, String questionnaireVersion,
                                           Map<String, Object> answers, UUID submittedBy, Instant now) {
        return new CatHealthSurvey(id, catId, questionnaireVersion, false, answers, submittedBy, now);
    }

    public static CatHealthSurvey skipped(UUID id, UUID catId, String questionnaireVersion,
                                          UUID submittedBy, Instant now) {
        return new CatHealthSurvey(id, catId, questionnaireVersion, true, Map.of(), submittedBy, now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCatId() {
        return catId;
    }

    public String getQuestionnaireVersion() {
        return questionnaireVersion;
    }

    public Map<String, Object> getAnswers() {
        return answers;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
