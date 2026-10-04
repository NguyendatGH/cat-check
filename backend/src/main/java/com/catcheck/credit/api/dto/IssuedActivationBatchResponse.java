package com.catcheck.credit.api.dto;

import java.time.Instant;

/**
 * Kết quả L20. <b>Không chứa mã thô</b> — mã thô chỉ đi ra qua L22
 * ({@code GET .../batches/{batchId}/csv}) đúng một lần, dạng {@code text/csv}.
 *
 * <p>Lý do tách: nhúng 50 000 mã vào một JSON response khiến chúng nằm trong log truy cập, trong
 * bộ nhớ đệm của proxy và trong tab Network của trình duyệt. {@code Content-Disposition:
 * attachment} của L22 đi thẳng xuống đĩa người vận hành.</p>
 *
 * @param csvPath đường dẫn gọi L22 — trả sẵn để UI không phải tự ghép chuỗi
 */
public record IssuedActivationBatchResponse(
        String batchId,
        String productionBatch,
        String packageCode,
        int quantity,
        Instant issuedAt,
        Instant validUntil,
        boolean csvAvailable,
        String csvPath
) {
}
