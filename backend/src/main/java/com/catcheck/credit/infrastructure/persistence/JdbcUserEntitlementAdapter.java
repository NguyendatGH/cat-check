package com.catcheck.credit.infrastructure.persistence;

import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.PlanFeatures;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcUserEntitlementAdapter implements UserEntitlementPort {

    private static final String COLUMNS = """
            user_id, highest_package, max_cat_profiles, features,
            write_access_until, trial_scans_used, recomputed_at
            """;

    private static final String DEFAULT_FEATURES_JSON = """
            {"history":"NONE","trend":false,"reminder":false,"export":false,"storeImage":false}""";

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    public JdbcUserEntitlementAdapter(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<Entitlement> find(UUID userId) {
        var rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM user_entitlement WHERE user_id = ?",
                (rs, rowNum) -> map(rs), userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Entitlement findOrDefault(UUID userId, Instant now) {
        return find(userId).orElseGet(() -> Entitlement.defaults(userId, now));
    }

    @Override
    public void ensureRow(UUID userId, Instant now) {
        jdbc.update("""
                INSERT INTO user_entitlement (user_id, features, trial_scans_used, recomputed_at)
                VALUES (?, ?::jsonb, 0, ?)
                ON CONFLICT (user_id) DO NOTHING
                """, userId, DEFAULT_FEATURES_JSON, RowReaders.utc(now));
    }

    @Override
    public void lockRow(UUID userId, Instant now) {
        ensureRow(userId, now);
        jdbc.query("SELECT 1 FROM user_entitlement WHERE user_id = ? FOR UPDATE", rs -> { }, userId);
    }

    @Override
    public void save(Entitlement entitlement) {
        jdbc.update("""
                INSERT INTO user_entitlement (
                    user_id, highest_package, max_cat_profiles, features,
                    write_access_until, trial_scans_used, recomputed_at
                ) VALUES (?, ?, ?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    highest_package    = EXCLUDED.highest_package,
                    max_cat_profiles   = EXCLUDED.max_cat_profiles,
                    features           = EXCLUDED.features,
                    write_access_until = EXCLUDED.write_access_until,
                    trial_scans_used   = EXCLUDED.trial_scans_used,
                    recomputed_at      = EXCLUDED.recomputed_at
                """,
                entitlement.userId(),
                entitlement.highestPackage(),
                entitlement.maxCatProfiles(),
                writeFeatures(entitlement.features()),
                RowReaders.utc(entitlement.writeAccessUntil()),
                entitlement.trialScansUsed(),
                RowReaders.utc(entitlement.recomputedAt()));
    }

    @Override
    public boolean incrementTrialScansUsed(UUID userId, int trialScanLimit, Instant now) {
        return jdbc.update("""
                UPDATE user_entitlement
                   SET trial_scans_used = trial_scans_used + 1, recomputed_at = ?
                 WHERE user_id = ? AND trial_scans_used < ?
                """, RowReaders.utc(now), userId, trialScanLimit) == 1;
    }

    @Override
    public Optional<Instant> maxActivatedBatchExpiry(UUID userId) {
        return Optional.ofNullable(jdbc.queryForObject("""
                SELECT MAX(expires_at) AS max_expiry FROM credit_batch WHERE user_id = ?
                """, (rs, rowNum) -> RowReaders.instant(rs, "max_expiry"), userId));
    }

    private Entitlement map(ResultSet rs) throws SQLException {
        return new Entitlement(
                RowReaders.uuid(rs, "user_id"),
                RowReaders.textOrNull(rs, "highest_package"),
                RowReaders.integerOrNull(rs, "max_cat_profiles"),
                readFeatures(RowReaders.text(rs, "features")),
                RowReaders.instant(rs, "write_access_until"),
                RowReaders.requiredInt(rs, "trial_scans_used"),
                RowReaders.requiredInstant(rs, "recomputed_at"));
    }

    private String writeFeatures(PlanFeatures features) {
        return jsonMapper.writeValueAsString(features);
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
