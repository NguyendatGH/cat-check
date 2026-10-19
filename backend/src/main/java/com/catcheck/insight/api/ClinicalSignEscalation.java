package com.catcheck.insight.api;

import java.util.List;
import java.util.UUID;

public interface ClinicalSignEscalation {

    EscalationResult reportClinicalSigns(UUID catId, UUID scanId, UUID clinicalSignReportId, List<String> signs);

    record EscalationResult(UUID healthFlagId, boolean blockingRequired, String explanationVi) {
    }
}
