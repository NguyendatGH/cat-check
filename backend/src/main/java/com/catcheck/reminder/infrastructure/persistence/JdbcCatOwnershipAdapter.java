package com.catcheck.reminder.infrastructure.persistence;

import com.catcheck.reminder.application.spi.CatOwnershipPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * {@link CatOwnershipPort} đọc thẳng bảng {@code cat} (V8) bằng JDBC read-only.
 *
 * <p>Không import type nào của {@code com.catcheck.cat.*} — xem javadoc {@link CatOwnershipPort}.</p>
 */
// Bean name PHẢI đặt tường minh: `scan.infrastructure.persistence` cũng có một class tên
// `JdbcCatOwnershipAdapter`, mà bean name mặc định sinh từ TÊN CLASS ĐƠN nên hai module
// đụng nhau -> `ConflictingBeanDefinitionException`, cả ứng dụng KHÔNG khởi động được.
// (Các cặp trùng tên còn lại — CatOwnershipPort, RowReaders, EnumParam... — không sao vì
// không phải bean Spring.)
@Repository("reminderJdbcCatOwnershipAdapter")
class JdbcCatOwnershipAdapter implements CatOwnershipPort {

    private final JdbcTemplate jdbc;

    JdbcCatOwnershipAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isOwnedAndAlive(UUID catId, UUID ownerId) {
        Integer found = jdbc.query(
                "SELECT 1 FROM cat WHERE id = ? AND owner_id = ? AND deleted_at IS NULL",
                rs -> rs.next() ? 1 : 0,
                catId, ownerId);
        return found != null && found == 1;
    }
}
