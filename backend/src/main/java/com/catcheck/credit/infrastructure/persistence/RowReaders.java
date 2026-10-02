package com.catcheck.credit.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tiện ích đọc cột dùng chung cho các adapter JDBC của module credit.
 *
 * <p>Cố ý <b>lặp lại</b> {@code identity.infrastructure.persistence.RowReaders} thay vì dùng
 * chung: bản đó là package-private của module identity, và đưa nó vào {@code shared} sẽ vượt
 * phạm vi sở hữu của A4. Một tệp tiện ích nhỏ lặp lại còn hơn là sửa file thuộc module khác.</p>
 *
 * <p>Mọi cột {@code TIMESTAMPTZ} đọc qua {@code getObject(col, OffsetDateTime.class)} rồi chuyển
 * sang {@link Instant}. KHÔNG dùng {@code getTimestamp()} — driver suy ra mốc giờ hiểu theo
 * {@code user.timezone} của JVM, nên giá trị đọc được phụ thuộc thiết bị chạy app (R13).</p>
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

    static int requiredInt(ResultSet rs, String column) throws SQLException {
        return rs.getInt(column);
    }

    static short requiredShort(ResultSet rs, String column) throws SQLException {
        return rs.getShort(column);
    }

    static boolean bool(ResultSet rs, String column) throws SQLException {
        return rs.getBoolean(column);
    }

    static <E extends Enum<E>> E enumValue(ResultSet rs, String column, Class<E> type) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }

    static <E extends Enum<E>> E requiredEnum(ResultSet rs, String column, Class<E> type) throws SQLException {
        E value = enumValue(rs, column, type);
        if (value == null) {
            throw new SQLException("Cột bắt buộc không có giá trị: " + column);
        }
        return value;
    }

    /** Chuyển {@link Instant} sang {@link OffsetDateTime} UTC để bind tham số. */
    static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
    }
}
