package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.application.spi.FeatureEntitlementPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

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
