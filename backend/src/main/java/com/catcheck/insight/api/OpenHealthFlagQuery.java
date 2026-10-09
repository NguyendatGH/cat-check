package com.catcheck.insight.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cổng đọc {@code health_flag} ĐANG MỞ (chưa acknowledge) của một mèo, dùng cho ngữ cảnh trợ lý
 * AI (module {@code ai}) — khác {@link HealthFlagExportQuery} (dùng cho {@code export}) ở chỗ
 * lọc theo {@code acknowledged_at IS NULL} thay vì theo khoảng thời gian.
 *
 * <p>{@code userId} là bắt buộc và được dùng lại nguyên vẹn luồng kiểm sở hữu của
 * {@code HealthFlagRepository#findByFilter} — đúng cổng đang phục vụ {@code GET /health-flags}.
 * Đây là lớp phòng thủ THỨ HAI: module {@code ai} đã kiểm {@code catId} thuộc {@code userId} một
 * lần ở {@code cat.api.CatContextQuery} trước khi gọi tới đây.</p>
 */
public interface OpenHealthFlagQuery {

    List<OpenFlag> openFlagsFor(UUID userId, UUID catId, int limit);

    /** {@code ruleCode}/{@code severity} thô (xem {@code insight.domain.MonitoringRuleCode}). */
    record OpenFlag(String ruleCode, String severity, Instant triggeredAt) {
    }
}
