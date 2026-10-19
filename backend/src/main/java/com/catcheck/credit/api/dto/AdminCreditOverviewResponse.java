package com.catcheck.credit.api.dto;

import java.util.List;

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

    public record LedgerPage(List<AdminLedgerEntryResponse> items, PageInfo page) {
    }

    public record PageInfo(
            int number, int size, long totalElements, int totalPages, boolean hasMore) {
    }
}
