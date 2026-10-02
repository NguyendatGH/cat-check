package com.catcheck.identity.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tien ich doc cot dung chung cho cac repository JDBC cua module identity.
 *
 * <p>Moi cot {@code TIMESTAMPTZ} deu doc qua {@code getObject(col, OffsetDateTime.class)}
 * roi chuyen sang {@link Instant}. KHONG dung {@code getTimestamp()} — driver se suy
 * ra muc gio hieu dua theo {@code user.timezone} cua JVM nen gia tri doc duoc phu
 * thuoc moi thiet bi chay app. {@code OffsetDateTime} giu nguyen muc UTC.</p>
 *
 * <p>R13 cam phu thuoc vao {@code java.sql.Timestamp}/{@code java.util.Date}/
 * {@code java.util.Calendar} o bat ky dau — ham nay la noi hop le duy nhat de cham
 * vao JDBC.</p>
 */
final class RowReaders {

    private RowReaders() {
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    /** Cho cot NOT NULL nhu {@code created_at}. */
    static Instant requiredInstant(ResultSet rs, String column) throws SQLException {
        Instant value = instant(rs, column);
        if (value == null) {
            throw new SQLException("Cot bat buoc khong co gia tri: " + column);
        }
        return value;
    }

    static Instant instantOrDefault(ResultSet rs, String column, Instant fallback) throws SQLException {
        Instant value = instant(rs, column);
        return value == null ? fallback : value;
    }

    static String text(ResultSet rs, String column) throws SQLException {
        return rs.getString(column);
    }

    static String textOrNull(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null || value.isEmpty() ? null : value;
    }

    static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    static UUID uuidOrNull(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    static Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /** Cho cot BIGINT co the NULL nhu {@code user_mfa_totp.last_used_step}. */
    static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static int requiredInt(ResultSet rs, String column) throws SQLException {
        return rs.getInt(column);
    }

    static boolean bool(ResultSet rs, String column) throws SQLException {
        return rs.getBoolean(column);
    }

    /**
     * Cho cot boolean co the NULL — vi du co doc ra tu JSONB ({@code app_user.notification_prefs}):
     * key thieu thi SQL tra NULL, va NULL o day nghia la "lay mac dinh cua mien", KHONG phai
     * {@code false} ({@code getBoolean} tra {@code false} cho ca hai nen phai hoi
     * {@code wasNull}).
     */
    static boolean boolOrDefault(ResultSet rs, String column, boolean fallback) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? fallback : value;
    }

    static byte[] bytesOrNull(ResultSet rs, String column) throws SQLException {
        return rs.getBytes(column);
    }

    /**
     * Doc enum tu {@code VARCHAR} cua DB. Tra {@code null} cho cot NULL; nem
     * {@link IllegalStateException} khi DB chua gia tri ma code khong biet —
     * {@link ClassCastException} se lam thong bao loi kho doc.
     */
    static <E extends Enum<E>> E enumValue(ResultSet rs, String column, Class<E> type) throws SQLException {
        String value = rs.getString(column);
        if (value == null) {
            return null;
        }
        return Enum.valueOf(type, value);
    }

    static <E extends Enum<E>> E requiredEnum(ResultSet rs, String column, Class<E> type) throws SQLException {
        E value = enumValue(rs, column, type);
        if (value == null) {
            throw new SQLException("Cot bat buoc khong co gia tri: " + column);
        }
        return value;
    }
}
