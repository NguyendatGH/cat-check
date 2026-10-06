package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.application.spi.OpenDsarPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * {@link OpenDsarPort} đọc trực tiếp {@code dsar_request} bằng {@code JdbcTemplate} — cùng lý do
 * kiến trúc với {@link JdbcCatOwnershipAdapter} (xem javadoc lớp đó và javadoc của cổng).
 *
 * <p>Tập trạng thái "đang mở" = <b>mọi thứ trừ</b> {@code COMPLETED} và {@code REJECTED}
 * (p4 B5: {@code RECEIVED} | {@code IDENTITY_PENDING} | {@code IN_PROGRESS} | {@code EXTENDED} |
 * {@code COMPLETED} | {@code REJECTED}). Viết dạng phủ định là cố ý: nếu p15 thêm một trạng thái
 * trung gian mới thì nó <b>mặc định là đang mở</b> — hướng sai lầm an toàn hơn ở đây là "DPO vẫn
 * làm được việc" chứ không phải "đột ngột mất quyền giữa lúc xử lý một yêu cầu pháp lý".</p>
 *
 * <p>Khớp cả yêu cầu của người đã mất tài khoản: {@code dsar_request.user_id} có thể NULL
 * (p4 B5) — dòng đó không khớp {@code user_id = ?} nên không mở quyền cho ai, đúng ý: không có
 * tài khoản thì cũng không có ảnh scan nào để xem.</p>
 * <p><b>Tên lớp mang tiền tố module</b>: bản song song ở
 * {@code cat.infrastructure.persistence.JdbcCatOpenDsarAdapter}. Trùng tên lớp đơn ở hai package
 * làm Spring sinh cùng một tên bean và context không khởi động được — xem handoff H15.153.</p>
 */
@Repository
class JdbcScanOpenDsarAdapter implements OpenDsarPort {

    private static final String EXISTS_OPEN = """
            SELECT EXISTS (
                SELECT 1 FROM dsar_request
                 WHERE user_id = ?
                   AND status NOT IN ('COMPLETED', 'REJECTED'))
            """;

    private final JdbcTemplate jdbc;

    JdbcScanOpenDsarAdapter(JdbcTemplate jdbc) {
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
