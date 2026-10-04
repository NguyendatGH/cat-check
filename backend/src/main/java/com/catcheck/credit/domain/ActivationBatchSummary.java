package com.catcheck.credit.domain;

import java.time.Instant;

/**
 * Một "lô mã kích hoạt" như màn {@code /admin/activation-codes} (p14 §14.3.2 mục 4) hiểu —
 * tổng hợp từ các dòng {@code activation_code} có cùng {@code production_batch}.
 *
 * <p><b>Không có bảng {@code activation_batch} trong p4.</b> Danh mục bảng ở p4 §4.9.2 /
 * {@code V10__credit.sql} chỉ có {@code activation_code}, và CLAUDE.md cấm thêm migration ngoài
 * danh mục đó. Vì vậy định danh lô ở đây là <b>chính chuỗi {@code production_batch}</b>, không
 * phải một UUID: nó đã là thứ p5 §5.5 dùng để nối lô cát với {@code color_chart}, nên không cần
 * phát minh thêm khoá. Hệ quả đã kiểm: {@code POST /admin/activation-codes/batch} từ chối
 * {@code productionBatch} đã tồn tại, nếu không thì "một lô" sẽ gồm nhiều lần sinh và các con
 * số dưới đây mất nghĩa.</p>
 *
 * @param productionBatch   định danh lô, đồng thời là {@code batchId} trên đường dẫn API
 * @param packageCode       gói của lô (một lô chỉ thuộc một gói — xem ràng buộc ở trên)
 * @param totalCodes        tổng số mã trong lô
 * @param issuedCodes       số mã còn {@code ISSUED} (chưa đổi, chưa huỷ)
 * @param redeemedCodes     số mã đã {@code REDEEMED}
 * @param voidedCodes       số mã đã {@code VOID}
 * @param issuedAt          thời điểm phát hành (nhỏ nhất trong lô)
 * @param validUntil        hạn kích hoạt (lớn nhất trong lô), có thể {@code null}
 */
public record ActivationBatchSummary(
        String productionBatch,
        String packageCode,
        long totalCodes,
        long issuedCodes,
        long redeemedCodes,
        long voidedCodes,
        Instant issuedAt,
        Instant validUntil
) {

    public ActivationBatchSummary {
        if (productionBatch == null || productionBatch.isBlank()) {
            throw new IllegalArgumentException("activationBatch.productionBatch phải có giá trị");
        }
    }
}
