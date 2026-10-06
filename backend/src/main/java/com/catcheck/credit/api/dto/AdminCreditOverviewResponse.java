package com.catcheck.credit.api.dto;

import java.util.List;

/**
 * {@code GET /api/v1/admin/users/{userId}/credits} (p8 L9) — lô credit + sổ ledger của một người
 * dùng.
 *
 * <p>Danh sách lô <b>không</b> phân trang (số lô mỗi tài khoản nhỏ, p8 §8.1.4 "không phân trang"),
 * sổ ledger phân trang <b>offset</b> (cột {@code Trang = O} của p8 L9).</p>
 *
 * @param availableBalance số dư khả dụng = {@code SUM(remaining_amount)} của lô còn hiệu lực (p5 R4)
 * @param batches          tất cả lô, mới nhất trước — kể cả lô đã đóng
 * @param ledger           một trang sổ cái + thông tin phân trang
 */
public record AdminCreditOverviewResponse(
        int availableBalance,
        List<AdminCreditBatchResponse> batches,
        LedgerPage ledger
) {

    public static AdminCreditOverviewResponse of(
            int availableBalance,
            List<AdminCreditBatchResponse> batches,
            List<AdminLedgerEntryResponse> ledger,
            int page,
            int size,
            long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceilDiv(totalElements, size);
        return new AdminCreditOverviewResponse(availableBalance, batches,
                new LedgerPage(ledger, new PageInfo(page, size, totalElements, totalPages,
                        (long) (page + 1) * size < totalElements)));
    }

    /** Sổ cái kèm envelope phân trang offset của p8 §8.1.4. */
    public record LedgerPage(List<AdminLedgerEntryResponse> items, PageInfo page) {
    }

    /** Biến thể offset của envelope phân trang chung (p8 §8.1.4). */
    public record PageInfo(
            int number, int size, long totalElements, int totalPages, boolean hasMore) {
    }
}
