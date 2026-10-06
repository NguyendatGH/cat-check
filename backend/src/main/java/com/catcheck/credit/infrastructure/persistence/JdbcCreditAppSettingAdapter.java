package com.catcheck.credit.infrastructure.persistence;

import com.catcheck.credit.application.spi.AppSettingPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.OptionalInt;

/**
 * {@link AppSettingPort} đọc trực tiếp bảng {@code app_setting} bằng {@code JdbcTemplate} — cùng
 * lý do kiến trúc với {@code cat.infrastructure.persistence.JdbcAppSettingAdapter}: bảng này
 * thuộc module {@code shared} về mặt sở hữu nghiệp vụ nhưng schema đã tồn tại từ {@code V7} nên
 * đọc được ngay, và cổng này chỉ đọc, không ghi.
 *
 * <p>Cột {@code value} là JSONB (không phải text thuần) — {@code value #>> '{}'} lấy biểu diễn
 * văn bản của giá trị JSON vô hướng.</p>
 * <p><b>Tên lớp mang tiền tố module</b> ({@code JdbcCredit...}) chứ không phải
 * {@code JdbcAppSettingAdapter} như bản của {@code cat}: Spring sinh tên bean từ tên lớp đơn, nên
 * hai lớp trùng tên ở hai package làm context <b>không khởi động được</b>
 * ({@code ConflictingBeanDefinitionException}) — phát hiện thật bằng
 * {@code ApplicationContextSmokeTest}. Đặt tên thay vì gán {@code @Repository("...")} để lỗi
 * không quay lại ở lần copy tiếp theo.</p>
 */
@Repository
class JdbcCreditAppSettingAdapter implements AppSettingPort {

    private final JdbcTemplate jdbc;

    JdbcCreditAppSettingAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OptionalInt findInt(String key) {
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
            // Giá trị lưu sai định dạng: coi như không đọc được, dùng mặc định trong mã (đúng
            // hợp đồng javadoc của AppSettingPort.findInt).
            return OptionalInt.empty();
        }
    }
}
