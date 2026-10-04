package com.catcheck.notification.infrastructure.persistence;

import com.catcheck.notification.application.spi.ConsentGatePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Consent gate đọc thẳng {@code consent_record} (p4 B2, bảng do module {@code privacy} sở hữu).
 *
 * <p><b>Vì sao không gọi {@code privacy.domain.port.ConsentStatePort}:</b>
 * {@code privacy/package-info.java} đã khai phụ thuộc {@code notification}, nên chiều ngược lại
 * tạo chu trình và {@code ModularityTests} đỏ. Đây là cùng judgment call đã ghi ở
 * {@code reminder/package-info.java} và {@code export}: đọc bảng của module khác bằng JDBC qua
 * một SPI hẹp, KHÔNG import type Java của module đó.</p>
 *
 * <p>Truy vấn bám đúng định nghĩa "hiện hành" của p4 B2: dòng <b>mới nhất</b> theo
 * {@code occurred_at} của cặp {@code (user, purpose)}. Bảng là append-only nên không được lấy
 * "có tồn tại dòng GRANTED" — rút consent cũng là một dòng mới, không xoá dòng cũ.</p>
 */
@Repository
class JdbcConsentGateAdapter implements ConsentGatePort {

    private final JdbcTemplate jdbc;

    JdbcConsentGateAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isGranted(UUID userId, String purposeCode) {
        return jdbc.query("""
                SELECT status
                  FROM consent_record
                 WHERE user_id = ? AND purpose_code = ?
                 ORDER BY occurred_at DESC, created_at DESC
                 LIMIT 1
                """, (rs, rowNum) -> "GRANTED".equals(rs.getString("status")), userId, purposeCode)
                .stream().findFirst()
                // Chưa có dòng nào ⇒ chưa đồng ý. "Im lặng không phải là đồng ý" (p15 §15.3.1 C4).
                .orElse(false);
    }
}
