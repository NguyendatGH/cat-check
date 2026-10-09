package com.catcheck.cat.application;

import com.catcheck.cat.api.CatContextQuery;
import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.CatHealthSurvey;
import com.catcheck.cat.domain.port.CatHealthSurveyRepository;
import com.catcheck.cat.domain.port.CatRepository;
import com.catcheck.cat.domain.port.ClinicalSignReportRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Hiện thực {@link CatContextQuery} (cổng do {@code cat} công bố cho {@code ai}). */
@Component
public class CatContextQueryAdapter implements CatContextQuery {

    /** Cửa sổ "dấu hiệu lâm sàng gần đây" của A4-(1), p4 §4.4.4. */
    private static final Duration CLINICAL_SIGN_WINDOW = Duration.ofDays(14);

    /** Đủ dư để sau khi lọc theo {@link #CLINICAL_SIGN_WINDOW} vẫn không bỏ sót bản khai nào. */
    private static final int CLINICAL_SIGN_FETCH_LIMIT = 20;

    private final CatRepository catRepository;
    private final CatHealthSurveyRepository surveyRepository;
    private final ClinicalSignReportRepository signReportRepository;
    private final Clock clock;

    public CatContextQueryAdapter(
            CatRepository catRepository,
            CatHealthSurveyRepository surveyRepository,
            ClinicalSignReportRepository signReportRepository,
            Clock clock) {
        this.catRepository = catRepository;
        this.surveyRepository = surveyRepository;
        this.signReportRepository = signReportRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CatProfileSnapshot> findSnapshot(UUID ownerId, UUID catId) {
        Optional<Cat> cat = catRepository.findByIdAndOwnerId(catId, ownerId);
        if (cat.isEmpty()) {
            return Optional.empty();
        }
        Cat owned = cat.get();
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);

        return Optional.of(new CatProfileSnapshot(
                owned.getId(),
                owned.getName(),
                owned.ageMonths(today),
                owned.getBreedCode(),
                owned.getBreedOther(),
                owned.getSex() == null ? null : owned.getSex().name(),
                owned.getNeutered(),
                owned.getWeightKg(),
                owned.getWeightUpdatedAt(),
                surveyOf(catId),
                recentClinicalSignsOf(catId, now)));
    }

    private SurveyAnswers surveyOf(UUID catId) {
        return surveyRepository.findLatestByCatId(catId)
                .filter(survey -> !survey.isSkipped())
                .map(this::toSurveyAnswers)
                .orElse(null);
    }

    private SurveyAnswers toSurveyAnswers(CatHealthSurvey survey) {
        Map<String, Object> answers = survey.getAnswers();
        return new SurveyAnswers(
                survey.getQuestionnaireVersion(),
                asString(answers, "litterType"),
                asString(answers, "urinaryHistory"),
                asString(answers, "dietType"),
                asString(answers, "urinationFrequency"),
                asStringList(answers, "symptoms"),
                survey.getSubmittedAt());
    }

    private List<ClinicalSignEntry> recentClinicalSignsOf(UUID catId, Instant now) {
        Instant since = now.minus(CLINICAL_SIGN_WINDOW);
        return signReportRepository.findByCatId(catId, CLINICAL_SIGN_FETCH_LIMIT).stream()
                .filter(report -> !report.getReportedAt().isBefore(since))
                .map(report -> new ClinicalSignEntry(
                        report.clinicalSigns().stream().map(Enum::name).toList(),
                        report.getReportedAt(),
                        report.getAcknowledgedAt() != null))
                .toList();
    }

    private static String asString(Map<String, Object> answers, String key) {
        Object value = answers.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> asStringList(Map<String, Object> answers, String key) {
        Object value = answers.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
