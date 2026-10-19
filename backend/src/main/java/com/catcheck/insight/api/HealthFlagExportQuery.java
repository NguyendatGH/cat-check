package com.catcheck.insight.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface HealthFlagExportQuery {

    List<ExportFlagRow> flagsInRange(UUID catId, Instant from, Instant to);

    record ExportFlagRow(String ruleCode, String severity, String explanationVi, Instant triggeredAt) {
    }
}
