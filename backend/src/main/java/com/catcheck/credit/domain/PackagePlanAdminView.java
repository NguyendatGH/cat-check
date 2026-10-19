package com.catcheck.credit.domain;

import java.time.Instant;

public record PackagePlanAdminView(PackagePlan plan, Instant updatedAt) {

    public PackagePlanAdminView {
        if (plan == null) {
            throw new IllegalArgumentException("packagePlanAdminView.plan bắt buộc");
        }
    }

    public String code() {
        return plan.code();
    }
}
