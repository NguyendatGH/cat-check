package com.catcheck.notification.infrastructure.persistence;

import com.catcheck.notification.application.spi.NotificationRecipientPort;
import com.catcheck.notification.domain.NotificationRecipient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Đọc {@code app_user} (p4 A1) để lấy địa chỉ nhận, locale và múi giờ.
 *
 * <p>Lọc sẵn các trạng thái không được gửi: {@code ANONYMIZED} (tài khoản đã ẩn danh — p15
 * §15.4.6 ghi rõ email đã bị ẩn danh hoá) và {@code DELETION_REQUESTED} trong thời gian chờ.
 * Gửi thông báo sản phẩm cho hai trạng thái này vừa vô nghĩa vừa là xử lý dữ liệu không có căn
 * cứ.</p>
 */
@Repository
class JdbcNotificationRecipientAdapter implements NotificationRecipientPort {

    private final JdbcTemplate jdbc;

    JdbcNotificationRecipientAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<NotificationRecipient> findById(UUID userId) {
        return jdbc.query("""
                SELECT id, email, locale, timezone
                  FROM app_user
                 WHERE id = ? AND status NOT IN ('ANONYMIZED', 'DELETION_REQUESTED')
                """, (rs, rowNum) -> new NotificationRecipient(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("locale"),
                        zone(rs.getString("timezone"))),
                userId).stream().findFirst();
    }

    @Override
    public List<UUID> findActiveRecipientIdsAfter(UUID afterId, int limit) {
        // `? IS NULL OR id > ?` thay vi ghep chuoi SQL co dieu kien: mot cau lenh duy nhat thi
        // PostgreSQL cache duoc plan, va khong co nhanh nao de lot mot bo loc trang thai.
        return jdbc.query("""
                SELECT id
                  FROM app_user
                 WHERE status NOT IN ('ANONYMIZED', 'DELETION_REQUESTED')
                   AND (CAST(? AS uuid) IS NULL OR id > CAST(? AS uuid))
                 ORDER BY id
                 LIMIT ?
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), afterId, afterId, limit);
    }

    /** Múi giờ hỏng không được làm chết việc gửi — rơi về mặc định của p12 §12.5.2. */
    private ZoneId zone(String raw) {
        if (raw == null || raw.isBlank()) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
        try {
            return ZoneId.of(raw);
        } catch (DateTimeException ex) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }
}
