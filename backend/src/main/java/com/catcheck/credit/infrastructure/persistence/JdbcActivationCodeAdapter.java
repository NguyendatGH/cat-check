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

/**
 * {@link ActivationCodePort} trên {@code JdbcTemplate}.
 *
 * <p>Adapter này <b>không bao giờ</b> ghi mã thô: cột duy nhất chứa dữ liệu mã là
 * {@code code_hash}. Không có phương thức nào đọc lại mã đã phát hành — đó là thiết kế, không
 * phải thiếu sót (p5 §5.9). Màn admin L19 vì vậy chỉ tra được theo {@code code_prefix}, gói,
 * trạng thái và lô sản xuất.</p>
 */
@Repository
public class JdbcActivationCodeAdapter implements ActivationCodePort {

    private static final String COLUMNS = """
            id, code_hash, pepper_version, code_prefix, package_code, production_batch,
            issued_at, valid_until, status, redeemed_by, redeemed_at
            """;

    /**
     * Phần SELECT dùng chung của L21/L22/L24: gom {@code activation_code} theo
     * {@code production_batch}.
     *
     * <p>{@code MIN(package_code)} chứ không đưa {@code package_code} vào GROUP BY: theo thiết
     * kế một lô chỉ thuộc một gói (service chặn trùng {@code production_batch} lúc phát hành),
     * nên gom thêm cột đó chỉ làm một lô bị tách đôi nếu dữ liệu cũ lỡ vi phạm — hiển thị một
     * dòng với gói đầu tiên trung thực hơn là hai dòng cùng {@code batchId}.</p>
     */
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
    public int markBatchVoid(String productionBatch) {
        return jdbc.update("""
                UPDATE activation_code SET status = 'VOID'
                 WHERE production_batch = ? AND status = 'ISSUED'
                """, productionBatch);
    }

    @Override
    public Optional<ActivationCode> lockById(UUID codeId) {
        // SELECT ... FOR UPDATE: hai request kích hoạt cùng một mã sẽ xếp hàng ở đây, nên
        // request thứ hai đọc được trạng thái MỚI NHẤT. Phải SELECT đủ cột rồi dựng lại object:
        // khoá không làm cho object đã nạp ở lần tra trước tự nhiên mới theo.
        return jdbc.query("SELECT " + COLUMNS + " FROM activation_code WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> map(rs), codeId).stream().findFirst();
    }

    /**
     * Mệnh đề WHERE dùng chung cho {@link #search} và {@link #count} — dựng một lần để hai câu
     * không bao giờ lệch bộ lọc (tổng số dòng khác danh sách là lỗi khó thấy nhất của phân
     * trang offset).
     */
    private static String whereOf(ActivationCodeFilter filter, List<Object> args) {
        StringBuilder where = new StringBuilder();
        if (filter.codePrefix() != null) {
            // LIKE 'x%' dùng được index ix_activation_code_prefix. ESCAPE để ký tự '%' hoặc '_'
            // người dùng gõ vào ô tìm kiếm không biến thành ký tự đại diện.
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

    /** Thoát ký tự đại diện của LIKE bằng ESCAPE '!' — '!' không có nghĩa trong code_prefix. */
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
