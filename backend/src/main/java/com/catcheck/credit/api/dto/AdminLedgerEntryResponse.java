package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.port.CreditLedgerQueryPort.LedgerRow;

import java.time.Instant;

/**
 * Một dòng sổ cái như màn quản trị thấy — phần {@code ledger} của
 * {@code GET /api/v1/admin/users/{userId}/credits} (p8 L9).
 *
 * <p><b>Không trả {@code refId}.</b> Với dòng {@code ADJUST}/{@code GRANT} do admin tạo,
 * {@code credit_ledger.ref_id} là id của <b>admin thao tác</b> — và p14 §14.4.4 bước 9 yêu cầu
 * không lộ danh tính admin ra ngoài {@code audit_log}. Trả {@code refType} là đủ để đọc được
 * "khoản này từ đâu ra" mà không tiết lộ ai.</p>
 *
 * @param entryId      id dòng sổ cái
 * @param type         {@code GRANT} | {@code CONSUME} | {@code EXPIRE} | {@code REFUND} | {@code ADJUST}
 * @param amount       dương = vào, âm = ra
 * @param balanceAfter số dư khả dụng toàn tài khoản sau giao dịch (p5 R4)
 * @param batchId      lô bị biến động, {@code null} khi dòng không gắn lô
 * @param packageCode  mã gói của lô đó
 * @param refType      loại tài nguyên tham chiếu ({@code SCAN}/{@code ACTIVATION}/{@code JOB}/{@code ADMIN})
 * @param note         ghi chú hiển thị cho người dùng
 * @param createdAt    lúc ghi dòng
 */
public record AdminLedgerEntryResponse(
        String entryId,
        String type,
        int amount,
        int balanceAfter,
        String batchId,
        String packageCode,
        String refType,
        String note,
        Instant createdAt
) {

    public static AdminLedgerEntryResponse from(LedgerRow row) {
        return new AdminLedgerEntryResponse(
                row.id().toString(),
                row.type().name(),
                row.amount(),
                row.balanceAfter(),
                row.batchId() == null ? null : row.batchId().toString(),
                row.packageCode(),
                row.refType() == null ? null : row.refType().name(),
                row.note(),
                row.createdAt());
    }
}
