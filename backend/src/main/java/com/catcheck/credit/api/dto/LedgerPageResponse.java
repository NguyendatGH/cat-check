package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.port.CreditLedgerQueryPort.LedgerPage;
import com.catcheck.credit.domain.port.CreditLedgerQueryPort.LedgerRow;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/v1/credits/ledger} (p8 H3).
 *
 * @param entries    các dòng của trang, mới nhất trước
 * @param hasMore    còn trang kế tiếp không
 * @param nextCursor khoá trang kế tiếp, {@code null} khi đã tới cuối
 */
public record LedgerPageResponse(
        List<LedgerEntryResponse> entries,
        boolean hasMore,
        String nextCursor
) {

    public static LedgerPageResponse from(LedgerPage page, String nextCursor) {
        return new LedgerPageResponse(
                page.entries().stream().map(LedgerEntryResponse::from).toList(),
                page.hasMore(),
                nextCursor);
    }

    /**
     * Một dòng sổ cái.
     *
     * @param type    loại biến động
     * @param amount  dương = vào, âm = ra
     * @param batchId lô bị biến động, {@code null} nếu ghi ở mức tài khoản
     */
    public record LedgerEntryResponse(
            String id,
            String type,
            int amount,
            int balanceAfter,
            String batchId,
            String packageCode,
            String refType,
            String note,
            Instant createdAt
    ) {

        static LedgerEntryResponse from(LedgerRow row) {
            return new LedgerEntryResponse(
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
}
