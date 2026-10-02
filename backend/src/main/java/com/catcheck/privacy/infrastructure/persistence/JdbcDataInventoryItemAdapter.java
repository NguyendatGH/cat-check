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
            code, category_vi, description_vi, description_en, sensitivity, legal_basis,
            purpose_codes, retention_policy_code, storage_location, cross_border, recipient,
            active, created_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcDataInventoryItemAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DataInventoryItem> findAllActive() {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM data_inventory_item
                 WHERE active
                 ORDER BY code
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
                        RowReaders.requiredInstant(rs, "created_at")));
    }
}
