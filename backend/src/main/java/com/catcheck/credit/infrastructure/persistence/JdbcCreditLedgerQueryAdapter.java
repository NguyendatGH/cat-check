package com.catcheck.credit.infrastructure.persistence;

import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.port.CreditLedgerQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcCreditLedgerQueryAdapter implements CreditLedgerQueryPort {

    private static final String SELECT_PAGE = """
            SELECT l.id, l.type, l.amount, l.balance_after, l.batch_id, l.ref_type, l.note, l.created_at,
                   b.package_code, b.expires_at
              FROM credit_ledger l
              LEFT JOIN credit_batch b ON b.id = l.batch_id
             WHERE l.user_id = ?
            """;

    private static final int HARD_LIMIT = 100;

    private final JdbcTemplate jdbc;

    public JdbcCreditLedgerQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public LedgerPage findByUser(UUID userId, LedgerCursor cursor, int limit) {
        int fetchSize = Math.min(limit + 1, HARD_LIMIT + 1);

        List<Object> args = new ArrayList<>();
        args.add(userId);
        String sql;
        if (cursor == null) {
            sql = SELECT_PAGE + " ORDER BY l.created_at DESC, l.id DESC LIMIT ?";
            args.add(fetchSize);
        } else {
            sql = SELECT_PAGE + """
                     AND (l.created_at, l.id) < (?, ?)
                     ORDER BY l.created_at DESC, l.id DESC
                     LIMIT ?
                    """;
            args.add(RowReaders.utc(cursor.sortKey()));
            args.add(cursor.id());
            args.add(fetchSize);
        }

        List<LedgerRow> fetched = jdbc.query(sql, (rs, rowNum) -> map(rs), args.toArray());

        boolean hasMore = fetched.size() > limit;
        List<LedgerRow> entries = hasMore ? List.copyOf(fetched.subList(0, limit)) : List.copyOf(fetched);

        LedgerCursor nextCursor = null;
        if (hasMore && !entries.isEmpty()) {
            LedgerRow last = entries.get(entries.size() - 1);
            nextCursor = new LedgerCursor(last.createdAt(), last.id());
        }
        return new LedgerPage(entries, hasMore, nextCursor);
    }

    @Override
    public LedgerOffsetPage findByUserForAdmin(UUID userId, int offset, int limit) {
        int safeLimit = Math.clamp(limit, 1, HARD_LIMIT);
        int safeOffset = Math.max(offset, 0);

        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM credit_ledger WHERE user_id = ?", Long.class, userId);

        String sql = SELECT_PAGE + " ORDER BY l.created_at DESC, l.id DESC LIMIT ? OFFSET ?";
        List<LedgerRow> entries =
                jdbc.query(sql, (rs, rowNum) -> map(rs), userId, safeLimit, safeOffset);
        return new LedgerOffsetPage(entries, total == null ? 0L : total);
    }

    private LedgerRow map(ResultSet rs) throws SQLException {
        return new LedgerRow(
                RowReaders.uuid(rs, "id"),
                RowReaders.requiredEnum(rs, "type", CreditLedgerType.class),
                RowReaders.requiredInt(rs, "amount"),
                RowReaders.requiredInt(rs, "balance_after"),
                RowReaders.uuidOrNull(rs, "batch_id"),
                RowReaders.textOrNull(rs, "package_code"),
                RowReaders.instant(rs, "expires_at"),
                RowReaders.enumValue(rs, "ref_type", CreditLedgerRefType.class),
                RowReaders.textOrNull(rs, "note"),
                RowReaders.requiredInstant(rs, "created_at"));
    }
}
