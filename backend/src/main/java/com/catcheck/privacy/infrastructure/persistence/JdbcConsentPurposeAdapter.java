package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.domain.ConsentPurpose;
import com.catcheck.privacy.domain.port.ConsentPurposePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link ConsentPurposePort} trên {@code JdbcTemplate} — danh mục cấu hình, chỉ đọc.
 * {@code CHECK (is_mandatory OR default_state = FALSE)} của V17... của V6 đã ép bất biến I17
 * ở DB; adapter không cần kiểm lại.
 */
@Repository
public class JdbcConsentPurposeAdapter implements ConsentPurposePort {

    private static final String COLUMNS = """
            code, label_vi, label_en, description_vi, description_en,
            is_mandatory, is_sensitive_data, default_state, phase, display_order,
            active, withdraw_effect, created_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcConsentPurposeAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ConsentPurpose> findAllActive() {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM consent_purpose
                 WHERE active
                 ORDER BY phase, display_order
                """, (rs, rowNum) -> new ConsentPurpose(
                        RowReaders.requiredString(rs, "code"),
                        RowReaders.requiredString(rs, "label_vi"),
                        RowReaders.string(rs, "label_en"),
                        RowReaders.requiredString(rs, "description_vi"),
                        RowReaders.string(rs, "description_en"),
                        rs.getBoolean("is_mandatory"),
                        rs.getBoolean("is_sensitive_data"),
                        rs.getBoolean("default_state"),
                        rs.getInt("phase"),
                        rs.getInt("display_order"),
                        rs.getBoolean("active"),
                        RowReaders.string(rs, "withdraw_effect"),
                        RowReaders.instant(rs, "created_at")));
    }

    @Override
    public Optional<ConsentPurpose> findByCode(String purposeCode) {
        return jdbc.query("""
                SELECT\s""" + COLUMNS + """
                  FROM consent_purpose
                 WHERE code = ?
                """, (rs, rowNum) -> new ConsentPurpose(
                        RowReaders.requiredString(rs, "code"),
                        RowReaders.requiredString(rs, "label_vi"),
                        RowReaders.string(rs, "label_en"),
                        RowReaders.requiredString(rs, "description_vi"),
                        RowReaders.string(rs, "description_en"),
                        rs.getBoolean("is_mandatory"),
                        rs.getBoolean("is_sensitive_data"),
                        rs.getBoolean("default_state"),
                        rs.getInt("phase"),
                        rs.getInt("display_order"),
                        rs.getBoolean("active"),
                        RowReaders.string(rs, "withdraw_effect"),
                        RowReaders.instant(rs, "created_at")),
                purposeCode).stream().findFirst();
    }
}
