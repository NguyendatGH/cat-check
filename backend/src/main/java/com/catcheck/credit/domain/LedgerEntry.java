package com.catcheck.credit.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng {@code credit_ledger} — append-only, không bao giờ UPDATE/DELETE (p4 §4.6.2).
 *
 * <p>Bất biến I1: {@code initial_amount + SUM(ledger.amount theo batch) = remaining_amount}.
 * Vì vậy MỌI thay đổi {@code remaining_amount} đều phải đi kèm đúng một dòng ledger trong
 * cùng transaction — đó là lý do {@code balanceAfter} được ghi kèm ngay từ đầu thay vì suy
 * ra sau.</p>
 *
 * @param id              UUID v7 sinh ở tầng ứng dụng (p4 §4.1.1)
 * @param userId          chủ sở hữu credit
 * @param batchId         lô bị biến động, {@code null} khi ghi nhận ở mức tài khoản
 * @param type            loại biến động, quyết định dấu của {@code amount}
 * @param amount          dương = vào, âm = ra; bất biến {@code amount != 0}
 * @param balanceAfter    số dư khả dụng TOÀN USER sau giao dịch (p5 R4)
 * @param refType         loại tài nguyên tham chiếu, {@code null} khi không có
 * @param refId           id tài nguyên tham chiếu — cố ý không phải FK (p4 §4.9.3)
 * @param idempotencyKey  khóa chống double-submit, {@code null} khi thao tác nền
 * @param note            ghi chú; bắt buộc với {@link CreditLedgerType#ADJUST}
 * @param createdAt       thời điểm ghi dòng
 */
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

    /** Độ dài cột {@code credit_ledger.idempotency_key}. */
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

    /**
     * Bản sao chính bản dùng để gắn {@code balanceAfter} đã biết — bất biến I1 buộc mọi
     * dòng của một giao dịch phải cùng một số dư sau giao dịch.
     */
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
