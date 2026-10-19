package com.catcheck.credit.domain;

public record PackagePlanUpdate(
        Integer creditAmount,
        Integer creditValidityDays,
        Integer maxCatProfiles,
        PlanFeatures features,
        Boolean active,
        boolean clearMaxCatProfiles
) {

    public PackagePlanUpdate {
        if (creditAmount != null && creditAmount <= 0) {
            throw new IllegalArgumentException("creditAmount phải > 0");
        }
        if (creditValidityDays != null && creditValidityDays <= 0) {
            throw new IllegalArgumentException("creditValidityDays phải > 0");
        }
        if (maxCatProfiles != null && maxCatProfiles <= 0) {
            throw new IllegalArgumentException("maxCatProfiles phải > 0 hoặc null (không giới hạn)");
        }
        if (clearMaxCatProfiles && maxCatProfiles != null) {
            throw new IllegalArgumentException(
                    "không thể vừa đặt maxCatProfiles vừa xoá về không giới hạn");
        }
    }

    public boolean isEmpty() {
        return creditAmount == null && creditValidityDays == null && maxCatProfiles == null
                && features == null && active == null && !clearMaxCatProfiles;
    }
}
