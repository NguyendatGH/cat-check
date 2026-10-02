package com.catcheck.credit.infrastructure.persistence;

import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeStatus;
import com.catcheck.credit.domain.port.ActivationCodePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ActivationCodePort} trên {@code JdbcTemplate}.
 *
 * <p>Adapter này <b>không bao giờ</b> ghi mã thô: cột duy nhất chứa dữ liệu mã là
 * {@code code_hash}. Không có phương thức nào đọc lại mã đã phát hành — đó là thiết kế, không
 * phải thiếu sót (p5 §5.9).</p>
 */
@Repository
public class JdbcActivationCodeAdapter implements ActivationCodePort {

    private static final String COLUMNS = """
            id, code_hash, pepper_version, code_prefix, package_code, production_batch,
            issued_at, valid_until, status, redeemed_by, redeemed_at
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

    /**
     * {@inheritDoc}
     *
     * <p>Điều kiện {@code status = 'ISSUED'} là điều kiện quyết định, không phải tối ưu: nếu
     * không có nó thì hai request kích hoạt cùng một mã có thể cùng thành công và tạo ra hai lô
     * credit — phá vỡ bất biến I24 ngay cả khi đã khoá dòng.</p>
     */
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
    public Optional<ActivationCode> lockById(UUID codeId) {
        // SELECT ... FOR UPDATE: hai request kích hoạt cùng một mã sẽ xếp hàng ở đây, nên
        // request thứ hai đọc được trạng thái MỚI NHẤT. Phải SELECT đủ cột rồi dựng lại object:
        // khoá không làm cho object đã nạp ở lần tra trước tự nhiên mới theo.
        return jdbc.query("SELECT " + COLUMNS + " FROM activation_code WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> map(rs), codeId).stream().findFirst();
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
