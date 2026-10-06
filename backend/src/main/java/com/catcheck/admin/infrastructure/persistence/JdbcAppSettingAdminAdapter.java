package com.catcheck.admin.infrastructure.persistence;

import com.catcheck.admin.domain.AppSettingRow;
import com.catcheck.admin.domain.port.AppSettingAdminPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link AppSettingAdminPort} tren {@code JdbcTemplate}, bang {@code app_setting} (p4 §H3).
 *
 * <p><b>{@code value #>> '{}'} chu khong {@code value::text}.</b> Cot la {@code JSONB}; voi mot
 * chuoi JSON thi {@code ::text} tra ve ca <b>dau ngoac kep</b> ({@code "0.0.0-local"}) con
 * {@code #>> '{}'} tra ve noi dung ({@code 0.0.0-local}). Dung {@code ::text} nghia la admin UI
 * hien mot gia tri co dau ngoac, va mot vong doc-sua-ghi se nhoi them mot lop ngoac nua moi
 * lan. Day la toan tu ma {@code JdbcAppSettingWriter} cua {@code shared} da dung, giu nhat
 * quan.</p>
 */
@Repository
public class JdbcAppSettingAdminAdapter implements AppSettingAdminPort {

    private static final String COLUMNS = """
            key, value #>> '{}' AS value_text, value_type, description, secret, updated_by, updated_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcAppSettingAdminAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AppSettingRow> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM app_setting ORDER BY key",
                (rs, rowNum) -> map(rs));
    }

    @Override
    public Optional<AppSettingRow> findByKey(String key) {
        return jdbc.query("SELECT " + COLUMNS + " FROM app_setting WHERE key = ?",
                (rs, rowNum) -> map(rs), key).stream().findFirst();
    }

    /**
     * <b>Khong co {@code ON CONFLICT}:</b> {@code UPDATE} tran nghia la khoa khong ton tai thi
     * {@code 0} dong bi sua va {@link #findByKey} sau do tra rong ⇒ L70 tra
     * {@code 404 SETTING_KEY_UNKNOWN} dung nhu p8 chot. Mot {@code INSERT ... ON CONFLICT} se
     * am tham tao khoa moi, va danh muc khoa thuoc p4 §H3 chu khong phai thu admin them qua API.
     *
     * <p><b>Boc gia tri theo {@code value_type} cua CHINH dong do</b>, lay bang mot
     * subquery trong cung cau lenh — khong doc truoc roi quyet dinh o Java, de khong co khoang
     * giua hai cau lenh cho ai doi {@code value_type}. {@code to_jsonb(?::numeric)} cho
     * {@code INT}, {@code to_jsonb(?::boolean)} cho {@code BOOL}, {@code ?::jsonb} cho
     * {@code JSON}, va {@code to_jsonb(?::text)} cho {@code STRING} — ca bon deu de PostgreSQL
     * ep kieu, nen mot gia tri sai dinh dang bi tu choi o DB chu khong bi ghi bien dang.</p>
     */
    @Override
    public Optional<AppSettingRow> updateValue(String key, String rawValue, UUID updatedBy) {
        Optional<AppSettingRow> current = findByKey(key);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        String valueType = current.get().valueType() == null
                ? "STRING"
                : current.get().valueType().toUpperCase(Locale.ROOT);
        String valueExpression = switch (valueType) {
            case "INT" -> "to_jsonb(CAST(? AS numeric))";
            case "BOOL" -> "to_jsonb(CAST(? AS boolean))";
            case "JSON" -> "CAST(? AS jsonb)";
            default -> "to_jsonb(CAST(? AS text))";
        };
        int updated = jdbc.update("""
                UPDATE app_setting
                   SET value = %s, updated_by = ?
                 WHERE key = ?
                """.formatted(valueExpression), rawValue, updatedBy, key);
        // `updated_at` do trigger set_updated_at cua V16 lo — khong dat tay o day, neu khong hai
        // nguon se cho hai gia tri khac nhau va ETag (dua tren updated_at) thanh khong on dinh.
        return updated == 1 ? findByKey(key) : Optional.empty();
    }

    private static AppSettingRow map(ResultSet rs) throws SQLException {
        return new AppSettingRow(
                rs.getString("key"),
                rs.getString("value_text"),
                rs.getString("value_type"),
                rs.getString("description"),
                rs.getBoolean("secret"),
                rs.getObject("updated_by", UUID.class),
                instant(rs.getObject("updated_at", OffsetDateTime.class)));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
