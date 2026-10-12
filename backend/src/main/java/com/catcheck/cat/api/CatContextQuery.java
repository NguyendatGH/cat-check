package com.catcheck.cat.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatContextQuery {

    Optional<CatProfileSnapshot> findSnapshot(UUID ownerId, UUID catId);

    record CatProfileSnapshot(
            UUID catId,
            String name,
            Integer ageMonths,
            String breedCode,
            String breedOther,
            String sex,
            Boolean neutered,
            BigDecimal weightKg,
            Instant weightUpdatedAt,
            SurveyAnswers survey,
            List<ClinicalSignEntry> recentClinicalSigns) {

        public CatProfileSnapshot {
            recentClinicalSigns = recentClinicalSigns == null ? List.of() : List.copyOf(recentClinicalSigns);
        }
    }

    record SurveyAnswers(
            String questionnaireVersion,
            String litterType,
            String urinaryHistory,
            String dietType,
            String urinationFrequency,
            List<String> symptoms,
            Instant submittedAt) {

        public SurveyAnswers {
            symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
        }
    }

    record ClinicalSignEntry(List<String> signs, Instant reportedAt, boolean acknowledged) {

        public ClinicalSignEntry {
            signs = signs == null ? List.of() : List.copyOf(signs);
        }
    }
}
