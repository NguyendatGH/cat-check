package com.catcheck.credit.infrastructure.persistence;

import com.catcheck.credit.domain.ActivationBatchSummary;
import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeFilter;
import com.catcheck.credit.domain.ActivationCodeStatus;
import com.catcheck.credit.domain.port.ActivationCodePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcActivationCodeAdapter implements ActivationCodePort {

    private static final String COLUMNS = """
            id, code_hash, pepper_version, code_prefix, package_code, production_batch,
            issued_at, valid_until, status, redeemed_by, redeemed_at
            """;

    private static final String BATCH_SELECT = """
            SELECT production_batch,
                   MIN(package_code)                            AS package_code,
                   COUNT(*)                                     AS total_codes,
                   COUNT(*) FILTER (WHERE status = 'ISSUED')    AS issued_codes,
                   COUNT(*) FILTER (WHERE status = 'REDEEMED')  AS redeemed_codes,
                   COUNT(*) FILTER (WHERE status = 'VOID')      AS voided_codes,
                   MIN(issued_at)                               AS issued_at,
                   MAX(valid_until)                             AS valid_until
              FROM activation_code
             WHERE production_batch IS NOT NULL
            """;

    private static final String INSERT = """
            INSERT INTO activation_code (
                id, code_hash, pepper_version, code_prefix, package_code,
                production_batch, issued_at, valid_until, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public JdbcActivationCodeAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ActivationCode> findByCodeHash(String codeHash) {
        List<ActivationCode> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM activation_code WHERE code_hash = ?",
                (rs, rowNum) -> map(rs), codeHash);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public void insert(ActivationCode code) {
        jdbc.update(INSERT,
                code.id(),
                code.codeHash(),
                code.pepperVersion(),
                code.codePrefix(),
                code.packageCode(),
                code.productionBatch(),
                RowReaders.utc(code.issuedAt()),
                RowReaders.utc(code.validUntil()),
                code.status().name());
    }

    @Override
    public void insertAll(List<ActivationCode> codes) {
        if (codes.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT, codes.stream()
                .map(code -> new Object[]{
                        code.id(),
                        code.codeHash(),
                        code.pepperVersion(),
                        code.codePrefix(),
                        code.packageCode(),
                        code.productionBatch(),
                        RowReaders.utc(code.issuedAt()),
                        RowReaders.utc(code.validUntil()),
                        code.status().name()})
                .toList());
    }

    @Override
    public boolean markRedeemed(UUID codeId, UUID userId, Instant redeemedAt) {
        return jdbc.update("""
                UPDATE activation_code
                   SET status = 'REDEEMED', redeemed_by = ?, redeemed_at = ?
                 WHERE id = ? AND status = 'ISSUED'
                """, userId, RowReaders.utc(redeemedAt), codeId) == 1;
    }

    @Override
    public boolean markVoid(UUID codeId) {
        return jdbc.update(
                "UPDATE activation_code SET status = 'VOID' WHERE id = ? AND status = 'ISSUED'",
                codeId) == 1;
    }

    @Override
    public List<ActivationCode> findIssuedCodes(
            String packageCode, ActivationCodeStatus status, int offset, int limit) {
        return jdbc.query("""
                SELECT %s FROM activation_code
                 WHERE package_code = ? AND status = ?
                 ORDER BY issued_at DESC, id
                 LIMIT ? OFFSET ?
                """.formatted(COLUMNS), (rs, rowNum) -> map(rs), packageCode, status.name(), limit, offset);
    }

    @Override
    public long countIssuedCodes(String packageCode, ActivationCodeStatus status) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM activation_code WHERE package_code = ? AND status = ?
                """, Long.class, packageCode, status.name());
        return count == null ? 0L : count;
    }

    @Override
    public Optional<ActivationCode> findById(UUID codeId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM activation_code WHERE id = ?",
                (rs, rowNum) -> map(rs), codeId).stream().findFirst();
    }

    @Override
    public List<ActivationCode> search(ActivationCodeFilter filter, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        String where = whereOf(filter, args);
        args.add(limit);
        args.add(offset);
        return jdbc.query(
                "SELECT %s FROM activation_code%s ORDER BY issued_at DESC, id LIMIT ? OFFSET ?"
                        .formatted(COLUMNS, where),
                (rs, rowNum) -> map(rs), args.toArray());
    }

    @Override
    public long count(ActivationCodeFilter filter) {
        List<Object> args = new ArrayList<>();
        String where = whereOf(filter, args);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activation_code" + where, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    @Override
    public List<ActivationBatchSummary> listBatches(int offset, int limit) {
        return jdbc.query(BATCH_SELECT
                        + " GROUP BY production_batch ORDER BY MIN(issued_at) DESC, production_batch"
                        + " LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapBatch(rs), limit, offset);
    }

    @Override
    public long countBatches() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT production_batch) FROM activation_code
                 WHERE production_batch IS NOT NULL
                """, Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public Optional<ActivationBatchSummary> findBatch(String productionBatch) {
        return jdbc.query(BATCH_SELECT + " AND production_batch = ? GROUP BY production_batch",
                        (rs, rowNum) -> mapBatch(rs), productionBatch)
                .stream().findFirst();
    }

    @Override
    public void lockProductionBatch(String productionBatch) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { }, productionBatch);
    }

    @Override
    public int markBatchVoid(String productionBatch) {
        return jdbc.update("""
                UPDATE activation_code SET status = 'VOID'
                 WHERE production_batch = ? AND status = 'ISSUED'
                """, productionBatch);
    }

    @Override
    public Optional<ActivationCode> lockById(UUID codeId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM activation_code WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> map(rs), codeId).stream().findFirst();
    }

    private static String whereOf(ActivationCodeFilter filter, List<Object> args) {
        StringBuilder where = new StringBuilder();
        if (filter.codePrefix() != null) {
            append(where, "code_prefix LIKE ? ESCAPE '!'");
            args.add(escapeLike(filter.codePrefix()) + "%");
        }
        if (filter.packageCode() != null) {
            append(where, "package_code = ?");
            args.add(filter.packageCode());
        }
        if (filter.status() != null) {
            append(where, "status = ?");
            args.add(filter.status().name());
        }
        if (filter.productionBatch() != null) {
            append(where, "production_batch = ?");
            args.add(filter.productionBatch());
        }
        return where.toString();
    }

    private static void append(StringBuilder where, String condition) {
        where.append(where.isEmpty() ? " WHERE " : " AND ").append(condition);
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private ActivationBatchSummary mapBatch(ResultSet rs) throws SQLException {
        return new ActivationBatchSummary(
                RowReaders.text(rs, "production_batch"),
                RowReaders.textOrNull(rs, "package_code"),
                rs.getLong("total_codes"),
                rs.getLong("issued_codes"),
                rs.getLong("redeemed_codes"),
                rs.getLong("voided_codes"),
                RowReaders.instant(rs, "issued_at"),
                RowReaders.instant(rs, "valid_until"));
    }

    private ActivationCode map(ResultSet rs) throws SQLException {
        return new ActivationCode(
                RowReaders.uuid(rs, "id"),
                RowReaders.text(rs, "code_hash"),
                RowReaders.requiredShort(rs, "pepper_version"),
                RowReaders.text(rs, "code_prefix"),
                RowReaders.text(rs, "package_code"),
                RowReaders.textOrNull(rs, "production_batch"),
                RowReaders.requiredInstant(rs, "issued_at"),
                RowReaders.instant(rs, "valid_until"),
                RowReaders.requiredEnum(rs, "status", ActivationCodeStatus.class),
                RowReaders.uuidOrNull(rs, "redeemed_by"),
                RowReaders.instant(rs, "redeemed_at"));
    }
}
