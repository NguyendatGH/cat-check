package com.catcheck.scan.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi một scan được lưu thành công (không phải {@code INCONCLUSIVE}) — nguồn
 * kích hoạt để module {@code insight} đánh giá rule R1-R4 (p6 §6.9, p7 §7.4.3).
 *
 * <p><b>Đồng bộ, cùng transaction</b> (p7 §7.4.5): publish bằng
 * {@code ApplicationEventPublisher.publishEvent} bên trong transaction của
 * {@code ScanPersistenceService} — listener của {@code insight} là {@code @EventListener}
 * THƯỜNG (không {@code @Async}, không {@code @TransactionalEventListener}), nên chạy ngay trong
 * cùng transaction: nếu insight ghi lỗi, cả giao dịch rollback cùng scan.</p>
 *
 * <p><b>Giới hạn đã biết (xem {@code docs/handovers/A6.md}):</b> vì {@code insight} phụ thuộc
 * {@code scan::api} (không phải chiều ngược lại), health_flag mới bắn ra trong listener KHÔNG
 * trả ngược được vào response đồng bộ của {@code POST /scans} — trường {@code triggeredFlags[]}
 * của response luôn rỗng ở M3; client xem cảnh báo mới qua {@code GET /health-flags}.</p>
 */
public record ScanSavedEvent(
        UUID scanId,
        UUID catId,
        UUID userId,
        String assignment,
        BigDecimal phValue,
        String classification,
        BigDecimal confidence,
        boolean nearBoundary,
        String calibrationMethod,
        Instant capturedAt,
        boolean chartIsPlaceholder
) {
}
