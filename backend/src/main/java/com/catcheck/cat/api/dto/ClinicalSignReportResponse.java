package com.catcheck.cat.api.dto;

import com.catcheck.cat.domain.CatClinicalSignReport;

public record ClinicalSignReportResponse(String reportId, TriggeredFlag triggeredFlag) {

    public static ClinicalSignReportResponse from(CatClinicalSignReport report) {
        return new ClinicalSignReportResponse(report.getId().toString(), null);
    }

    public record TriggeredFlag(String flagId, String ruleCode, String severity) {
    }
}
