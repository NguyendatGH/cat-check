package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.application.spi.OpenDsarPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * {@link OpenDsarPort} đọc trực tiếp {@code dsar_request} bằng {@code JdbcTemplate} — cùng lý do
 * kiến trúc với {@link JdbcAppSettingAdapter} (xem javadoc lớp đó và javadoc của cổng).
 *
 * <p>Tập trạng thái "đang mở" viết dạng <b>phủ định</b> ({@code NOT IN ('COMPLETED','REJECTED')},
 * p4 B5) là cố ý: nếu p15 thêm một trạng thái trung gian mới thì nó mặc định là <i>đang mở</i> —
 * hướng sai lầm an toàn hơn ở đây là "DPO vẫn làm được việc" chứ không phải "đột ngột mất quyền
 * giữa lúc xử lý một yêu cầu pháp lý".</p>
 * <p><b>Tên lớp mang tiền tố module</b>: bản song song ở
 * {@code scan.infrastructure.persistence.JdbcScanOpenDsarAdapter}. Trùng tên lớp đơn ở hai
 * package làm Spring sinh cùng một tên bean và context không khởi động được — xem handoff
 * H15.153.</p>
 */
@Repository
class JdbcCatOpenDsarAdapter implements OpenDsarPort {

    private static final String EXISTS_OPEN = """
            SELECT EXISTS (
                SELECT 1 FROM dsar_request
                 WHERE user_id = ?
                   AND status NOT IN ('COMPLETED', 'REJECTED'))
            """;

    private final JdbcTemplate jdbc;

    JdbcCatOpenDsarAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasOpenRequestFor(UUID userId) {
        if (userId == null) {
            return false;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(EXISTS_OPEN, Boolean.class, userId));
    }
}
