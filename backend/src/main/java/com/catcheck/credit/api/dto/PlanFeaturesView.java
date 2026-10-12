package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.HistoryLevel;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PlanFeatures;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PlanFeaturesView(
        @NotBlank @Pattern(regexp = "NONE|BASIC|ADVANCED") String history,
        @NotNull Boolean trend,
        @NotNull Boolean reminder,
        @NotNull Boolean export,
        @NotNull Boolean storeImage
) {

    public static PlanFeaturesView from(PackagePlan plan) {
        PlanFeatures features = plan.features();
        return new PlanFeaturesView(
                features.history().name(),
                features.trend(),
                features.reminder(),
                features.export(),
                features.storeImage());
    }

    public PlanFeatures toDomain() {
        return new PlanFeatures(HistoryLevel.valueOf(history),
                Boolean.TRUE.equals(trend), Boolean.TRUE.equals(reminder),
                Boolean.TRUE.equals(export), Boolean.TRUE.equals(storeImage));
    }
}
