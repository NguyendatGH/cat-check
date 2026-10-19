package com.catcheck.credit.domain;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(
        UUID id,
        UUID userId,
        UUID batchId,
        CreditLedgerType type,
        int amount,
        int balanceAfter,
        CreditLedgerRefType refType,
        UUID refId,
        String idempotencyKey,
        String note,
        Instant createdAt
) {

    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    public LedgerEntry {
        if (id == null || userId == null || type == null || createdAt == null) {
            throw new IllegalArgumentException("ledgerEntry thiếu trường bắt buộc");
        }
        if (amount == 0) {
            throw new IllegalArgumentException("ledgerEntry.amount không được bằng 0 (ck_credit_ledger_amount)");
        }
        if (!signMatchesType(type, amount)) {
            throw new IllegalArgumentException(
                    "Dấu của amount không khớp type " + type + " (ck_credit_ledger_sign): " + amount);
        }
        if (idempotencyKey != null && idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "ledgerEntry.idempotencyKey dài hơn " + MAX_IDEMPOTENCY_KEY_LENGTH);
        }
        if (type.requiresNote() && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("Dòng ADJUST bắt buộc có note (ck_credit_ledger_adjust_note)");
        }
        if (balanceAfter < 0) {
            throw new IllegalArgumentException("ledgerEntry.balanceAfter không được âm");
        }
    }

    public LedgerEntry withBalanceAfter(int newBalanceAfter) {
        return new LedgerEntry(id, userId, batchId, type, amount, newBalanceAfter,
                refType, refId, idempotencyKey, note, createdAt);
    }

    private static boolean signMatchesType(CreditLedgerType type, int amount) {
        return switch (type) {
            case GRANT, REFUND -> amount > 0;
            case CONSUME, EXPIRE -> amount < 0;
            case ADJUST -> true;
        };
    }
}
