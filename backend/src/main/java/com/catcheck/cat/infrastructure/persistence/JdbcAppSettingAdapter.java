package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.application.spi.AppSettingPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * {@link AppSettingPort} đọc trực tiếp bảng {@code app_setting} bằng {@code JdbcTemplate} — cùng
 * lý do kiến trúc với {@code JdbcUserAccountPortAdapter} (xem javadoc lớp đó). Bảng này thuộc
 * module {@code shared} về mặt sở hữu nghiệp vụ (W3, xem chú thích trong chính
 * {@code V7__catalog.sql}: "app_setting -> module shared (W3) — A3 chỉ tạo schema"), nhưng schema
 * đã tồn tại từ V7 (do chính module cat migrate) nên đọc được ngay — không cần chờ W3 cấp entity
 * dùng chung, và cổng này chỉ đọc, không ghi.
 *
 * <p>Cột {@code value} là JSONB (không phải text thuần) — {@code value #>> '{}'} lấy biểu diễn văn
 * bản của giá trị JSON vô hướng (số/băn/chuỗi), tương thích với cả số nguyên lẫn chuỗi số.</p>
 *
 * <p>{@code requesterId} của {@link AppSettingPort#findInt(String, UUID)} không dùng ở đây:
 * {@code app_setting} là key/value TOÀN CỤC, không có cột theo user — tham số này tồn tại trong
 * hợp đồng cổng để dành cho ghi log/audit về sau, không phải để lọc theo người dùng.</p>
 */
@Repository
class JdbcAppSettingAdapter implements AppSettingPort {

    private final JdbcTemplate jdbc;

    JdbcAppSettingAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OptionalInt findInt(String key, UUID requesterId) {
        List<String> rows = jdbc.query(
                "SELECT value #>> '{}' AS value_text FROM app_setting WHERE key = ? AND value_type = 'INT'",
                (rs, rowNum) -> rs.getString("value_text"),
                key);
        if (rows.isEmpty() || rows.getFirst() == null) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(rows.getFirst().trim()));
        } catch (NumberFormatException ex) {
            // Giá trị lưu sai định dạng: coi như không đọc được, dùng mặc định trong mã (theo
            // đúng hợp đồng javadoc của AppSettingPort.findInt).
            return OptionalInt.empty();
        }
    }
}
