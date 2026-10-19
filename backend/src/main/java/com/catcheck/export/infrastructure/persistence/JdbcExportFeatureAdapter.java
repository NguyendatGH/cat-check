package com.catcheck.export.infrastructure.persistence;

import com.catcheck.export.application.spi.ExportFeaturePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class JdbcExportFeatureAdapter implements ExportFeaturePort {

    private final JdbcTemplate jdbc;

    JdbcExportFeatureAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isExportEnabled(UUID userId) {
        List<String> rows = jdbc.query(
                "SELECT features ->> 'export' AS value FROM user_entitlement WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("value"),
                userId);
        if (rows.isEmpty()) {
            return false;
        }
        String value = rows.getFirst();
        return value != null && !"false".equalsIgnoreCase(value) && !"NONE".equalsIgnoreCase(value);
    }
}
