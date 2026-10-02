package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.application.spi.FeatureEntitlementPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * {@link FeatureEntitlementPort} doc cot JSONB {@code user_entitlement.features}
 * ({@code V10__credit.sql}) bang {@code JdbcTemplate}.
 *
 * <p>Cung khuon {@code JdbcCatProfileLimitAdapter} — KHONG import type nao cua
 * {@code com.catcheck.credit.*}. Xem javadoc {@link FeatureEntitlementPort}.</p>
 *
 * <p>{@code features} co dang {@code {history:'ADVANCED', trend:true, reminder:true, export:true,
 * storeImage:true}} (p5 §5.3) — gia tri co the la boolean HOAC chuoi (vd {@code history}), nen
 * doc ra text roi quy uoc: bat khi khac {@code false}/{@code null}/{@code 'NONE'}.</p>
 */
@Repository
class JdbcFeatureEntitlementAdapter implements FeatureEntitlementPort {

    private final JdbcTemplate jdbc;

    JdbcFeatureEntitlementAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isFeatureEnabled(UUID userId, String featureKey) {
        List<String> rows = jdbc.query(
                "SELECT features ->> ? AS value FROM user_entitlement WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("value"),
                featureKey, userId);
        if (rows.isEmpty()) {
            return false;
        }
        String value = rows.getFirst();
        return value != null && !"false".equalsIgnoreCase(value) && !"NONE".equalsIgnoreCase(value);
    }
}
