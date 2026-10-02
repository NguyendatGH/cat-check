package com.catcheck.scan.api;

import java.util.UUID;

/**
 * Sự kiện phát ra khi một scan bị gán lại cho mèo khác (p6 §6.10.3, p8 E9) — đồng bộ, cùng
 * transaction với {@code UPDATE scan} + {@code INSERT scan_reassignment}.
 *
 * <p><b>Phạm vi MVP (xem {@code docs/handovers/A6.md}):</b> insight chỉ thu hồi các
 * {@code health_flag} có {@code trigger_scan_id = scanId} (mồ côi rõ ràng). KHÔNG tính lại toàn
 * bộ rule R1-R3 cho cả hai mèo (p6 §6.10.3 đầy đủ) — đó là tính năng lớn hơn, ngoài ngân sách
 * thời gian của module này; baseline/streak của cả hai mèo sẽ tự đúng dần ở các lần quét sau.</p>
 */
public record ScanReassignedEvent(UUID scanId, UUID fromCatId, UUID toCatId) {
}
