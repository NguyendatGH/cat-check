package com.catcheck.scan.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tiện ích đọc cột dùng chung cho các adapter JDBC của module {@code scan}.
 *
 * <p>Cố ý <b>lặp lại</b> {@code credit.infrastructure.persistence.RowReaders} /
 * {@code identity.infrastructure.persistence.RowReaders} thay vì dùng chung — cùng lý do đã ghi
 * ở bản của module credit: các lớp đó package-private, đưa vào {@code shared} vượt phạm vi sở
 * hữu. Mọi cột {@code TIMESTAMPTZ} đọc qua {@code OffsetDateTime} (không {@code getTimestamp()},
 * R13).
 */
final class RowReaders {

    private RowReaders() {
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static Instant requiredInstant(ResultSet rs, String column) throws SQLException {
        Instant value = instant(rs, column);
        if (value == null) {
            throw new SQLException("Cột bắt buộc không có giá trị: " + column);
        }
        return value;
    }

    static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(java.time.ZoneOffset.UTC);
    }

    static String textOrNull(ResultSet rs, String column) throws SQLException {
        return rs.getString(column);
    }

    static UUID uuidOrNull(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    static UUID requiredUuid(ResultSet rs, String column) throws SQLException {
        UUID value = uuidOrNull(rs, column);
        if (value == null) {
            throw new SQLException("Cột bắt buộc không có giá trị: " + column);
        }
        return value;
    }

    static Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static BigDecimal decimalOrNull(ResultSet rs, String column) throws SQLException {
        return rs.getBigDecimal(column);
    }

    static boolean bool(ResultSet rs, String column) throws SQLException {
        return rs.getBoolean(column);
    }

    static <E extends Enum<E>> E enumValue(ResultSet rs, String column, Class<E> type) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }
}
