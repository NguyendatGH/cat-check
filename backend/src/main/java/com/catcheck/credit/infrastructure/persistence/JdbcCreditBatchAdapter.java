package com.catcheck.credit.infrastructure.persistence;

import com.catcheck.credit.domain.CreditBatchGrant;
import com.catcheck.credit.domain.CreditBatchStatus;
import com.catcheck.credit.domain.CreditBatchView;
import com.catcheck.credit.domain.ExpiryReminderMilestone;
import com.catcheck.credit.domain.port.CreditBatchPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@link CreditBatchPort} trên {@code JdbcTemplate}. */
@Repository
public class JdbcCreditBatchAdapter implements CreditBatchPort {

    private static final String COLUMNS = """
            id, package_code, initial_amount, remaining_amount, activated_at, expires_at, status
            """;

    private static final String INSERT = """
            INSERT INTO credit_batch (
                id, user_id, activation_code_id, package_code, package_version,
                initial_amount, remaining_amount, activated_at, expires_at, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public JdbcCreditBatchAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Ghi {@code remaining_amount = initial_amount} và {@code status = 'ACTIVE'}: lô mới luôn
     * mở. Hai cột {@code t48h_notified_at} / {@code t6h_notified_at} để NULL — job nhắc sẽ tự
     * đánh dấu (p12 §12.6).</p>
     */
    @Override
    public void insertNewBatch(CreditBatchGrant batch) {
        jdbc.update(INSERT,
                batch.id(),
                batch.userId(),
                batch.activationCodeId(),
                batch.packageCode(),
                batch.packageVersion(),
                batch.creditAmount(),
                batch.creditAmount(),
                RowReaders.utc(batch.activatedAt()),
                RowReaders.utc(batch.expiresAt()),
                CreditBatchStatus.ACTIVE.name());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Lọc {@code status = 'ACTIVE' AND expires_at > ?} thay vì dựa vào {@code remaining_amount}
     * một mình: lô đã quá hạn mà {@code ExpireCreditBatchesJob} chưa chạy tới vẫn phải bị loại
     * khỏi số dư khả dụng, nếu không user dùng được credit đã hết hạn.</p>
     */
    @Override
    public List<CreditBatchView> findLiveBatches(UUID userId, Instant now) {
        return jdbc.query("""
                SELECT %s FROM credit_batch
                 WHERE user_id = ? AND status = 'ACTIVE' AND expires_at > ? AND remaining_amount > 0
                 ORDER BY expires_at, id
                """.formatted(COLUMNS), (rs, rowNum) -> map(rs), userId, RowReaders.utc(now));
    }

    @Override
    public List<CreditBatchView> findAllBatches(UUID userId) {
        return jdbc.query("""
                SELECT %s FROM credit_batch WHERE user_id = ? ORDER BY activated_at DESC, id
                """.formatted(COLUMNS), (rs, rowNum) -> map(rs), userId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Câu này khớp đúng index partial {@code ix_credit_batch_expiring} (V10, "chỉ chứa lô
     * còn sống nên rất nhỏ") nên quét toàn hệ thống mỗi giờ vẫn rẻ. {@code LIMIT} là yêu cầu
     * của p12 §12.6.1 quy tắc 3a, không phải tối ưu tuỳ chọn: thời gian chạy phải dự đoán được
     * để không chạm {@code lockAtMostFor}.</p>
     */
    @Override
    public List<UUID> findDueForExpiry(Instant now, int limit) {
        return jdbc.queryForList("""
                SELECT id FROM credit_batch
                 WHERE status = 'ACTIVE' AND expires_at <= ? AND remaining_amount > 0
                 ORDER BY expires_at, id
                 LIMIT ?
                """, UUID.class, RowReaders.utc(now), limit);
    }

    @Override
    public int countDueForExpiry(Instant now) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM credit_batch
                 WHERE status = 'ACTIVE' AND expires_at <= ? AND remaining_amount > 0
                """, Integer.class, RowReaders.utc(now));
        return count == null ? 0 : count;
    }

    @Override
    public List<UUID> findDueForExpiryReminder(
            ExpiryReminderMilestone milestone, Instant now, int limit) {
        return jdbc.queryForList(reminderQuery(milestone, "id", " ORDER BY expires_at, id LIMIT ?"),
                UUID.class,
                RowReaders.utc(now),
                RowReaders.utc(now.plus(milestone.lead())),
                limit);
    }

    @Override
    public int countDueForExpiryReminder(ExpiryReminderMilestone milestone, Instant now) {
        Integer count = jdbc.queryForObject(reminderQuery(milestone, "count(*)", ""),
                Integer.class,
                RowReaders.utc(now),
                RowReaders.utc(now.plus(milestone.lead())));
        return count == null ? 0 : count;
    }

    /**
     * Một khuôn SQL cho cả hai mốc nhắc. Tên cột cờ ghép bằng chuỗi — an toàn vì nó đến từ
     * {@link #notifiedColumn(ExpiryReminderMilestone)}, tức từ enum, không từ dữ liệu bên ngoài;
     * PostgreSQL không cho tham số hoá tên cột nên đây là cách duy nhất không phải viết hai câu
     * gần như giống hệt rồi để chúng lệch nhau khi sửa.
     */
    private static String reminderQuery(ExpiryReminderMilestone milestone, String select, String tail) {
        return """
                SELECT %s FROM credit_batch
                 WHERE status = 'ACTIVE'
                   AND remaining_amount > 0
                   AND expires_at > ?
                   AND expires_at <= ?
                   AND %s IS NULL
                """.formatted(select, notifiedColumn(milestone)) + tail;
    }

    /** Cột cờ "đã nhắc mốc này" trên {@code credit_batch} (p4 §4.5.1). */
    private static String notifiedColumn(ExpiryReminderMilestone milestone) {
        return switch (milestone) {
            case T48H -> "t48h_notified_at";
            case T6H -> "t6h_notified_at";
        };
    }

    private CreditBatchView map(ResultSet rs) throws SQLException {
        return new CreditBatchView(
                RowReaders.uuid(rs, "id"),
                RowReaders.text(rs, "package_code"),
                RowReaders.requiredInt(rs, "initial_amount"),
                RowReaders.requiredInt(rs, "remaining_amount"),
                RowReaders.requiredInstant(rs, "activated_at"),
                RowReaders.requiredInstant(rs, "expires_at"),
                RowReaders.requiredEnum(rs, "status", CreditBatchStatus.class));
    }
}
