package com.catcheck.insight.api;

import com.catcheck.insight.api.dto.HealthFlagListResponse;
import com.catcheck.insight.api.dto.HealthFlagResponse;
import com.catcheck.insight.api.dto.MonitoringRuleListResponse;
import com.catcheck.insight.api.dto.MonitoringRuleResponse;
import com.catcheck.insight.application.HealthFlagQueryService;
import com.catcheck.insight.application.MonitoringRuleCatalogService;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Nhóm G — Cảnh báo (chỉ mục health-flags, p8 §8.4.7 G1-G3). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Insight", description = "Dấu hiệu theo dõi xu hướng sức khoẻ (health_flag)")
public class InsightController {

    private final HealthFlagQueryService queryService;
    private final MonitoringRuleCatalogService ruleCatalogService;

    public InsightController(
            HealthFlagQueryService queryService,
            MonitoringRuleCatalogService ruleCatalogService) {
        this.queryService = queryService;
        this.ruleCatalogService = ruleCatalogService;
    }

    /**
     * F4 — danh mục rule cảnh báo đang bật. CÔNG KHAI (p8 §8.4.6 cột Auth = {@code —}).
     * Chỉ trả phần giải thích; tham số thuật toán là nội bộ — xem
     * {@link MonitoringRuleResponse}.
     */
    @Operation(
            operationId = "listMonitoringRules",
            summary = "F4 — Danh mục rule cảnh báo",
            description = "Công khai. Giải thích cho người dùng \"vì sao tôi bị flag\". "
                    + "`messageKey` là khoá i18n, client tự resolve (quyết định #15).")
    @GetMapping("/reference/monitoring-rules")
    public ResponseEntity<MonitoringRuleListResponse> listMonitoringRules() {
        List<MonitoringRuleResponse> items = ruleCatalogService.listEnabledRules().stream()
                .map(MonitoringRuleResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(new MonitoringRuleListResponse(items));
    }

    /** G1 — {@code GET /health-flags}. */
    @Operation(operationId = "listHealthFlags", summary = "Danh sách dấu hiệu theo dõi",
            description = "Lọc `catId`, `acknowledged`, `severity`; phân trang con trỏ.")
    @GetMapping("/health-flags")
    public HealthFlagListResponse listHealthFlags(
            @CurrentUser SecurityPrincipal user,
            @RequestParam(required = false) UUID catId,
            @RequestParam(required = false) Boolean acknowledged,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        var page = queryService.list(user.userId(), catId, acknowledged, severity, cursor, limit == null ? 20 : limit);
        List<HealthFlagResponse> items = page.items().stream().map(HealthFlagResponse::from).toList();
        return new HealthFlagListResponse(items, page.nextCursor() != null, page.nextCursor());
    }

    /** G2 — {@code GET /health-flags/{flagId}}. */
    @Operation(operationId = "getHealthFlag", summary = "Chi tiết một dấu hiệu theo dõi")
    @GetMapping("/health-flags/{flagId}")
    public HealthFlagResponse getHealthFlag(@CurrentUser SecurityPrincipal user, @PathVariable UUID flagId) {
        return HealthFlagResponse.from(queryService.detail(user.userId(), flagId));
    }

    /** G3 — {@code POST /health-flags/{flagId}/acknowledge}. */
    @Operation(operationId = "acknowledgeHealthFlag", summary = "Đánh dấu \"Đã hiểu\" cho một dấu hiệu")
    @PostMapping("/health-flags/{flagId}/acknowledge")
    public ResponseEntity<Void> acknowledgeHealthFlag(@CurrentUser SecurityPrincipal user, @PathVariable UUID flagId) {
        queryService.acknowledge(user.userId(), flagId);
        return ResponseEntity.noContent().build();
    }
}
