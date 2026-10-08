package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.domain.DataInventoryItem;
import com.catcheck.privacy.domain.InventorySensitivity;
import com.catcheck.privacy.domain.LegalBasis;
import com.catcheck.privacy.domain.port.DataInventoryItemPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link DataInventoryItemPort} trên {@code JdbcTemplate} — khối "Dữ liệu CatCheck đang giữ
 * về bạn" render động từ bảng này (p15 §15.4.3).
 */
@Repository
public class JdbcDataInventoryItemAdapter implements DataInventoryItemPort {

    private static final String COLUMNS = """
            d.code, d.category_vi, d.description_vi, d.description_en, d.sensitivity, d.legal_basis,
            d.purpose_codes, d.retention_policy_code, d.storage_location, d.cross_border, d.recipient,
            d.active, d.created_at, rp.retention_days
            """;

    private final JdbcTemplate jdbc;

    public JdbcDataInventoryItemAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DataInventoryItem> findAllActive() {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM data_inventory_item d
                  LEFT JOIN retention_policy rp ON rp.code = d.retention_policy_code
                 WHERE d.active
                 ORDER BY d.code
                """, (rs, rowNum) -> new DataInventoryItem(
                        RowReaders.requiredString(rs, "code"),
                        RowReaders.requiredString(rs, "category_vi"),
                        RowReaders.requiredString(rs, "description_vi"),
                        RowReaders.string(rs, "description_en"),
                        InventorySensitivity.valueOf(rs.getString("sensitivity")),
                        LegalBasis.valueOf(rs.getString("legal_basis")),
                        RowReaders.stringList(rs, "purpose_codes"),
                        RowReaders.string(rs, "retention_policy_code"),
                        RowReaders.requiredString(rs, "storage_location"),
                        rs.getBoolean("cross_border"),
                        RowReaders.string(rs, "recipient"),
                        rs.getBoolean("active"),
                        RowReaders.requiredInstant(rs, "created_at"),
                        (Integer) rs.getObject("retention_days")));
    }
}
