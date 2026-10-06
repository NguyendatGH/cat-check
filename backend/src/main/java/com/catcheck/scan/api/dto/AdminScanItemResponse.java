package com.catcheck.scan.api.dto;

import com.catcheck.scan.application.AdminFreeTextMask;
import com.catcheck.scan.domain.port.ScanQueryRepository.Row;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một dòng của {@code GET /api/v1/admin/users/{userId}/scans} (p8 L5).
 *
 * <p><b>Không có trường nào trỏ tới ảnh.</b> p11 §11.5.4 dòng "Ảnh scan": Support <b>Không</b>,
 * Super <b>Không</b>, DPO chỉ khi có DSAR — nên đường duy nhất tới ảnh là L6, và
 * {@link #imageStored} (chỉ "có hay không") cũng chỉ được điền khi
 * {@code fullData} (DPO đang xử lý DSAR).</p>
 *
 * @param scanId         id lần quét
 * @param catId          mèo được gán, {@code null} khi {@code SHARED_UNKNOWN}/{@code UNASSIGNED}
 * @param catName        tên mèo — đã che khi {@code fullData = false} (p11 §11.5.4)
 * @param assignment     {@code scan.assignment}
 * @param capturedAt     thời điểm chụp
 * @param status         {@code scan.status}
 * @param phValue        pH đo được, {@code null} với {@code INCONCLUSIVE}
 * @param classification nhãn phân loại thô (client tra {@code label_key} riêng — không đặt chữ
 *                       ở server)
 * @param confidence     độ tin cậy
 * @param nearBoundary   kết quả nằm sát ranh giới dải
 * @param labL           toạ độ Lab L*
 * @param labA           toạ độ Lab a*
 * @param labB           toạ độ Lab b*
 * @param calibrationMethod phương pháp hiệu chỉnh màu
 * @param engineVersion  phiên bản engine đã tính dòng này
 * @param creditCharged  lần quét này có trừ credit không
 * @param trial          lần quét trial (p5 R6)
 * @param disputed       người dùng đã đánh dấu kết quả sai
 * @param disputedNote   ghi chú tranh chấp — đã che khi {@code fullData = false}
 * @param reassignCount  số lần đã đổi mèo
 * @param imageStored    có ảnh gốc không; {@code null} với mọi vai trò không phải DPO-có-DSAR
 */
public record AdminScanItemResponse(
        String scanId,
        String catId,
        String catName,
        String assignment,
        Instant capturedAt,
        String status,
        BigDecimal phValue,
        String classification,
        BigDecimal confidence,
        boolean nearBoundary,
        BigDecimal labL,
        BigDecimal labA,
        BigDecimal labB,
        String calibrationMethod,
        String engineVersion,
        boolean creditCharged,
        boolean trial,
        boolean disputed,
        String disputedNote,
        short reassignCount,
        Boolean imageStored
) {

    public static AdminScanItemResponse from(Row row, String catName, boolean fullData) {
        return new AdminScanItemResponse(
                row.scanId().toString(),
                row.catId() == null ? null : row.catId().toString(),
                fullData ? catName : AdminFreeTextMask.text(catName),
                row.assignment(),
                row.capturedAt(),
                row.status(),
                row.phValue(),
                row.classification(),
                row.confidence(),
                row.nearBoundary(),
                row.labL(),
                row.labA(),
                row.labB(),
                row.calibrationMethod(),
                row.engineVersion(),
                row.creditCharged(),
                row.trial(),
                row.disputedAt() != null,
                fullData ? row.disputedNote() : AdminFreeTextMask.text(row.disputedNote()),
                row.reassignCount(),
                fullData ? row.imageStored() : null);
    }
}
