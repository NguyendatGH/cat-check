package com.catcheck.credit.infrastructure.persistence;

import com.catcheck.credit.domain.CreditBatchSnapshot;
import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.ExpiringCreditBatch;
import com.catcheck.credit.domain.ExpiryReminderMilestone;
import com.catcheck.credit.domain.LedgerEntry;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCreditLedgerAdapter implements CreditLedgerPort {

    private static final String INSERT = """
            INSERT INTO credit_ledger (
                id, user_id, batch_id, type, amount, balance_after,
                ref_type, ref_id, idempotency_key, note
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String COLUMNS = """
            id, user_id, batch_id, type, amount, balance_after,
            ref_type, ref_id, idempotency_key, note, created_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcCreditLedgerAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CreditBatchSnapshot> lockLiveBatchesForFefo(UUID userId, Instant now) {
        return jdbc.query("""
                SELECT id, expires_at, remaining_amount, initial_amount
                  FROM credit_batch
                 WHERE user_id = ?
                   AND status = 'ACTIVE'
                   AND expires_at > ?
                   AND remaining_amount > 0
                 ORDER BY expires_at, id
                 FOR UPDATE
                """, (rs, rowNum) -> new CreditBatchSnapshot(
                        RowReaders.uuid(rs, "id"),
                        RowReaders.requiredInstant(rs, "expires_at"),
                        RowReaders.requiredInt(rs, "remaining_amount"),
                        RowReaders.requiredInt(rs, "initial_amount")),
                userId, RowReaders.utc(now));
    }

    @Override
    public Optional<CreditBatchSnapshot> lockBatchForRefund(UUID batchId) {
        return jdbc.query("""
                SELECT id, expires_at, remaining_amount, initial_amount
                  FROM credit_batch
                 WHERE id = ?
                 FOR UPDATE
                """, (rs, rowNum) -> new CreditBatchSnapshot(
                        RowReaders.uuid(rs, "id"),
                        RowReaders.requiredInstant(rs, "expires_at"),
                        RowReaders.requiredInt(rs, "remaining_amount"),
                        RowReaders.requiredInt(rs, "initial_amount")),
                batchId).stream().findFirst();
    }

    @Override
    public Optional<ExpiringCreditBatch> lockBatchForExpiry(UUID batchId, Instant now) {
        return jdbc.query("""
                SELECT id, user_id, expires_at, remaining_amount, initial_amount
                  FROM credit_batch
                 WHERE id = ?
                   AND status = 'ACTIVE'
                   AND expires_at <= ?
                   AND remaining_amount > 0
                 FOR UPDATE SKIP LOCKED
                """, (rs, rowNum) -> mapExpiring(rs), batchId, RowReaders.utc(now))
                .stream().findFirst();
    }

    @Override
    public Optional<ExpiringCreditBatch> lockBatchForExpiryReminder(
            UUID batchId, ExpiryReminderMilestone milestone, Instant now) {
        return jdbc.query("""
                SELECT id, user_id, expires_at, remaining_amount, initial_amount
                  FROM credit_batch
                 WHERE id = ?
                   AND status = 'ACTIVE'
                   AND remaining_amount > 0
                   AND expires_at > ?
                   AND expires_at <= ?
                   AND %s IS NULL
                 FOR UPDATE SKIP LOCKED
                """.formatted(notifiedColumn(milestone)),
                (rs, rowNum) -> mapExpiring(rs),
                batchId, RowReaders.utc(now), RowReaders.utc(now.plus(milestone.lead())))
                .stream().findFirst();
    }

    private static String notifiedColumn(ExpiryReminderMilestone milestone) {
        return switch (milestone) {
            case T48H -> "t48h_notified_at";
            case T6H -> "t6h_notified_at";
        };
    }

    private static ExpiringCreditBatch mapExpiring(ResultSet rs) throws SQLException {
        return new ExpiringCreditBatch(
                RowReaders.uuid(rs, "id"),
                RowReaders.uuid(rs, "user_id"),
                RowReaders.requiredInstant(rs, "expires_at"),
                RowReaders.requiredInt(rs, "remaining_amount"),
                RowReaders.requiredInt(rs, "initial_amount"));
    }

    @Override
    public void updateRemaining(UUID batchId, int remainingAmount) {
        int updated = jdbc.update(
                "UPDATE credit_batch SET remaining_amount = ? WHERE id = ?",
                remainingAmount, batchId);
        if (updated != 1) {
            throw new IllegalStateException("Không cập nhật được credit_batch đang khoá: " + batchId);
        }
    }

    @Override
    public void markExhausted(UUID batchId) {
        jdbc.update("""
                UPDATE credit_batch
                   SET status = 'EXHAUSTED', remaining_amount = 0
                 WHERE id = ? AND status = 'ACTIVE'
                """, batchId);
    }

    @Override
    public void markActive(UUID batchId) {
        jdbc.update("""
                UPDATE credit_batch
                   SET status = 'ACTIVE'
                 WHERE id = ? AND status = 'EXHAUSTED'
                """, batchId);
    }

    @Override
    public void markExpired(UUID batchId) {
        jdbc.update("""
                UPDATE credit_batch
                   SET status = 'EXPIRED', remaining_amount = 0
                 WHERE id = ? AND status = 'ACTIVE'
                """, batchId);
    }

    @Override
    public void markT48hNotified(UUID batchId) {
        jdbc.update("UPDATE credit_batch SET t48h_notified_at = now() WHERE id = ?", batchId);
    }

    @Override
    public void markT6hNotified(UUID batchId) {
        jdbc.update("UPDATE credit_batch SET t6h_notified_at = now() WHERE id = ?", batchId);
    }

    @Override
    public void append(LedgerEntry entry) {
        jdbc.update(INSERT,
                entry.id(),
                entry.userId(),
                entry.batchId(),
                entry.type().name(),
                entry.amount(),
                entry.balanceAfter(),
                entry.refType() == null ? null : entry.refType().name(),
                entry.refId(),
                entry.idempotencyKey(),
                entry.note());
    }

    @Override
    public int availableBalance(UUID userId, Instant now) {
        Integer sum = jdbc.queryForObject("""
                SELECT COALESCE(SUM(remaining_amount), 0)
                  FROM credit_batch
                 WHERE user_id = ? AND status = 'ACTIVE' AND expires_at > ?
                """, Integer.class, userId, RowReaders.utc(now));
        return sum == null ? 0 : sum;
    }

    @Override
    public int sumLedgerAmountForBatch(UUID batchId) {
        Integer sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM credit_ledger WHERE batch_id = ?",
                Integer.class, batchId);
        return sum == null ? 0 : sum;
    }

    @Override
    public Optional<LedgerEntryRef> findById(UUID ledgerEntryId) {
        List<LedgerEntryRef> rows = jdbc.query("""
                SELECT id, type, batch_id, user_id, amount FROM credit_ledger WHERE id = ?
                """, (rs, rowNum) -> new LedgerEntryRef(
                        RowReaders.uuid(rs, "id"),
                        RowReaders.requiredEnum(rs, "type", CreditLedgerType.class),
                        RowReaders.uuid(rs, "batch_id"),
                        RowReaders.uuid(rs, "user_id"),
                        RowReaders.requiredInt(rs, "amount")),
                ledgerEntryId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey) {
        List<LedgerEntry> rows = jdbc.query("""
                SELECT %s FROM credit_ledger WHERE idempotency_key = ?
                """.formatted(COLUMNS), (rs, rowNum) -> map(rs), idempotencyKey);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<LedgerEntry> findConsumeRowsByRef(CreditLedgerRefType refType, UUID refId) {
        return jdbc.query("""
                SELECT %s FROM credit_ledger
                 WHERE ref_type = ? AND ref_id = ? AND type = 'CONSUME'
                 ORDER BY created_at, id
                """.formatted(COLUMNS), (rs, rowNum) -> map(rs), refType.name(), refId);
    }

    @Override
    public int sumAdminAdjustedAbsSince(UUID adminId, Instant since) {
        Integer total = jdbc.queryForObject("""
                SELECT COALESCE(SUM(abs(amount)), 0) FROM credit_ledger
                 WHERE ref_type = 'ADMIN' AND ref_id = ? AND created_at >= ?
                """, Integer.class, adminId, RowReaders.utc(since));
        return total == null ? 0 : total;
    }

    @Override
    public boolean existsRefundReferencing(UUID consumedLedgerEntryId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM credit_ledger
                                WHERE type = 'REFUND' AND ref_id = ?)
                """, Boolean.class, consumedLedgerEntryId);
        return Boolean.TRUE.equals(exists);
    }

    private LedgerEntry map(ResultSet rs) throws SQLException {
        return new LedgerEntry(
                RowReaders.uuid(rs, "id"),
                RowReaders.uuid(rs, "user_id"),
                RowReaders.uuidOrNull(rs, "batch_id"),
                RowReaders.requiredEnum(rs, "type", CreditLedgerType.class),
                RowReaders.requiredInt(rs, "amount"),
                RowReaders.requiredInt(rs, "balance_after"),
                RowReaders.enumValue(rs, "ref_type", com.catcheck.credit.domain.CreditLedgerRefType.class),
                RowReaders.uuidOrNull(rs, "ref_id"),
                RowReaders.textOrNull(rs, "idempotency_key"),
                RowReaders.textOrNull(rs, "note"),
                RowReaders.requiredInstant(rs, "created_at"));
    }
}
