package com.catcheck.credit.domain;

public record PlanFeatures(
        HistoryLevel history,
        boolean trend,
        boolean reminder,
        boolean export,
        boolean storeImage
) {

    public static PlanFeatures none() {
        return new PlanFeatures(HistoryLevel.NONE, false, false, false, false);
    }

    public boolean isEnabled(PlanFeature feature) {
        return switch (feature) {
            case HISTORY -> history.grantsHistory();
            case TREND -> trend;
            case REMINDER -> reminder;
            case EXPORT -> export;
            case STORE_IMAGE -> storeImage;
        };
    }
}
