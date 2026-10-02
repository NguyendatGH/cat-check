package com.catcheck.credit.infrastructure.persistence;

import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PlanFeatures;
import com.catcheck.credit.domain.port.PackagePlanPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * {@link PackagePlanPort} trên {@code JdbcTemplate} — chỉ ĐỌC {@code package_plan}.
 *
 * <p>Bảng do module cat/content tạo ở {@code V7__catalog.sql}; module credit không có bản ghi nào
 * trong đó nên adapter này không có phương thức ghi, và không có {@code @Transactional} ở đâu cả
 * (đọc một bảng cấu hình bất biến theo request không cần transaction).</p>
 */
@Repository
public class JdbcPackagePlanAdapter implements PackagePlanPort {

    private static final String COLUMNS = """
            code, name, weight_kg, credit_amount, credit_validity_days,
            max_cat_profiles, features, active, version
            """;

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    public JdbcPackagePlanAdapter(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<PackagePlan> findByCode(String code) {
        List<PackagePlan> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM package_plan WHERE code = ?", this::mapRow, code);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /** F3 — chỉ gói đang bán; sắp theo khối lượng tăng dần (p5 §5.2: gói định danh theo kg). */
    @Override
    public List<PackagePlan> findAllActive() {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM package_plan WHERE active = true ORDER BY weight_kg",
                this::mapRow);
    }

    private PackagePlan mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PackagePlan(
                RowReaders.text(rs, "code"),
                RowReaders.text(rs, "name"),
                rs.getBigDecimal("weight_kg"),
                RowReaders.requiredInt(rs, "credit_amount"),
                RowReaders.requiredInt(rs, "credit_validity_days"),
                RowReaders.integerOrNull(rs, "max_cat_profiles"),
                readFeatures(RowReaders.textOrNull(rs, "features")),
                RowReaders.bool(rs, "active"),
                RowReaders.requiredInt(rs, "version"));
    }

    /** Cột {@code features} JSONB; cấu hình hỏng thì hạ về tập quyền tối thiểu thay vì ném. */
    private PlanFeatures readFeatures(String json) {
        if (json == null || json.isBlank()) {
            return PlanFeatures.none();
        }
        try {
            return jsonMapper.readValue(json, PlanFeatures.class);
        } catch (RuntimeException ex) {
            return PlanFeatures.none();
        }
    }
}
