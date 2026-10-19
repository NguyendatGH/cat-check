package com.catcheck.privacy.api.dto;

import java.util.List;

public record PolicyVersionListResponse(
        String policyCode,
        List<PolicyVersionSummaryView> items
) {
}
