package com.catcheck.credit.infrastructure.persistence;

import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PackagePlanAdminView;
import com.catcheck.credit.domain.PackagePlanUpdate;
import com.catcheck.credit.domain.PlanFeatures;
import com.catcheck.credit.domain.port.PackagePlanPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    @Override
    public List<PackagePlan> findAllActive() {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM package_plan WHERE active = true ORDER BY weight_kg",
                this::mapRow);
    }

    @Override
    public List<PackagePlanAdminView> findAllForAdmin(boolean includeInactive) {
        String where = includeInactive ? "" : " WHERE active = true";
        return jdbc.query("SELECT " + COLUMNS + ", updated_at FROM package_plan" + where
                + " ORDER BY weight_kg, code", this::mapAdminRow);
    }

    @Override
    public Optional<PackagePlanAdminView> findForAdmin(String code) {
        return jdbc.query("SELECT " + COLUMNS + ", updated_at FROM package_plan WHERE code = ?",
                this::mapAdminRow, code).stream().findFirst();
    }

    @Override
    public boolean update(String code, PackagePlanUpdate update) {
        if (update.isEmpty()) {
            return false;
        }
        StringBuilder sql = new StringBuilder("UPDATE package_plan SET version = version + 1");
        List<Object> args = new ArrayList<>();
        if (update.creditAmount() != null) {
            sql.append(", credit_amount = ?");
            args.add(update.creditAmount());
        }
        if (update.creditValidityDays() != null) {
            sql.append(", credit_validity_days = ?");
            args.add(update.creditValidityDays());
        }
        if (update.maxCatProfiles() != null) {
            sql.append(", max_cat_profiles = ?");
            args.add(update.maxCatProfiles());
        }
        if (update.clearMaxCatProfiles()) {
            sql.append(", max_cat_profiles = NULL");
        }
        if (update.features() != null) {
            sql.append(", features = ?::jsonb");
            args.add(jsonMapper.writeValueAsString(update.features()));
        }
        if (update.active() != null) {
            sql.append(", active = ?");
            args.add(update.active());
        }
        sql.append(" WHERE code = ?");
        args.add(code);
        return jdbc.update(sql.toString(), args.toArray()) == 1;
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

    private PackagePlanAdminView mapAdminRow(ResultSet rs, int rowNum) throws SQLException {
        return new PackagePlanAdminView(mapRow(rs, rowNum), RowReaders.instant(rs, "updated_at"));
    }

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
