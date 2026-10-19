package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.port.CreditLedgerQueryPort.LedgerRow;

import java.time.Instant;

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
