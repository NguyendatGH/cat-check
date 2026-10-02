package com.catcheck.privacy.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Đọc cột an toàn từ {@code ResultSet} — tham khảo {@code credit.infrastructure.persistence.RowReaders}.
 * Mọi adapter của privacy dùng chung để không lặp kiểm tra null/kiểu.
 */
final class RowReaders {

    private RowReaders() {
    }

    static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof UUID uuid ? uuid : null;
    }

    static String string(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return rs.wasNull() ? null : value;
    }

    static String requiredString(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (rs.wasNull()) {
            throw new IllegalStateException("Cột " + column + " đáng lẽ NOT NULL lại đọc được null");
        }
        return value;
    }

    /**
     * Đọc cột {@code TIMESTAMPTZ} qua {@code getObject(col, OffsetDateTime.class)} rồi chuyển
     * sang {@link Instant} — KHÔNG dùng {@code getTimestamp()}: đó là {@code java.sql.Timestamp},
     * bị cấm bởi ArchUnit R13 (cùng khuôn với {@code credit.infrastructure.persistence.RowReaders}).
     */
    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static Instant requiredInstant(ResultSet rs, String column) throws SQLException {
        Instant value = instant(rs, column);
        if (value == null) {
            throw new IllegalStateException("Cột " + column + " đáng lẽ NOT NULL lại đọc được null");
        }
        return value;
    }

    static int intOrDefault(ResultSet rs, String column, int defaultValue) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? defaultValue : value;
    }

    static boolean boolOrDefault(ResultSet rs, String column, boolean defaultValue) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? defaultValue : value;
    }

    static Integer boxedInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static java.time.LocalDate localDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    @SuppressWarnings("unchecked")
    static java.util.List<String> stringList(ResultSet rs, String column) throws SQLException {
        java.sql.Array array = rs.getArray(column);
        if (array == null || rs.wasNull()) {
            return java.util.List.of();
        }
        return java.util.List.of((String[]) array.getArray());
    }
}
