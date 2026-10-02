package com.catcheck.insight.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Cổng đọc cho module {@code export} — liệt kê flag đã bắn trong một khoảng thời gian (p13 Khối 3). */
public interface HealthFlagExportQuery {

    List<ExportFlagRow> flagsInRange(UUID catId, Instant from, Instant to);

    record ExportFlagRow(String ruleCode, String severity, String explanationVi, Instant triggeredAt) {
    }
}
