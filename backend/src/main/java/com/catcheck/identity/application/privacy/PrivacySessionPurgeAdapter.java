package com.catcheck.identity.application.privacy;

import com.catcheck.identity.domain.SessionRevokeReason;
import com.catcheck.privacy.spi.GlobalSessionPurgePort;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Adapter identity → {@link GlobalSessionPurgePort}: bước <b>R1</b> của sổ tay sự cố
 * (p11 §11.13.4), đường "Ưu tiên 1 — qua endpoint quản trị (có audit tự động)" mà chính
 * runbook đó chỉ định, thay cho việc gõ tay hai câu {@code DELETE} vào DB production.
 *
 * <p><b>Vì sao phải xoá thật {@code SPRING_SESSION} chứ không chỉ đánh dấu
 * {@code user_device_session}:</b> p11 §11.1.3 ghi nhận {@code SPRING_SESSION.SESSION_ID}
 * lưu <b>nguyên văn</b> — ai đọc được bảng đó mạo danh được mọi phiên đang mở. Và trong
 * code hiện tại {@code user_device_session} chỉ là <b>bản sao để hiển thị</b>: không có
 * filter nào đối chiếu {@code revoked_at} của nó trên đường request, nên
 * {@code AuthenticatedSessionRevoker.revokeAllByUserId} (L7/L12) <b>không thực sự đẩy ai ra
 * khỏi hệ thống</b> — xem handoff H15.176. L62 không được phép có cùng khoảng trống đó, vì
 * nó là biện pháp ngăn chặn khi DB đã bị rò.
 *
 * <p><b>Thao tác theo tập hợp, không vòng lặp theo user:</b> mục tiêu ≤ 5 phút của p8 L62
 * là cho toàn hệ thống. {@code SPRING_SESSION_ATTRIBUTES} có
 * {@code ON DELETE CASCADE} (V3, nguyên văn schema upstream) nên xoá bảng cha là đủ —
 * runbook liệt kê hai câu {@code DELETE} vì nó mô tả đường thủ công an toàn cho mọi phiên
 * bản schema.</p>
 *
 * <p><b>Ba thứ phải vô hiệu trong CÙNG thao tác</b> (§11.13.4: "nếu bỏ sót, kẻ tấn công vẫn
 * còn đường vào"): phiên HTTP, mọi {@code email_otp} đang mở, và mọi ticket chưa dùng trên
 * {@code email_otp}. Hai thứ còn lại của bảng đó không cần câu lệnh riêng: {@code mfaLevel}
 * và {@code reauthAt} là thuộc tính phiên nên chết cùng phiên, còn link tải gói DSAR/PDF
 * trong code này đều đòi phiên đăng nhập (và L62 còn hết hạn token DSAR riêng ở
 * {@code GlobalSessionPurgeService}).</p>
 */
@Component
class PrivacySessionPurgeAdapter implements GlobalSessionPurgePort {

    /**
     * {@code ck_device_session_reason} (V5) chỉ nhận 8 giá trị và <b>không có giá trị nào cho
     * R1</b>; {@code ADMIN_LOCK} là giá trị đúng về <i>người gây ra</i> (một admin) nhưng sai
     * về <i>nguyên nhân</i> (tài khoản không bị khoá). Cần thêm giá trị vào enum + CHECK —
     * handoff H15.177.
     */
    private static final SessionRevokeReason REVOKE_REASON = SessionRevokeReason.ADMIN_LOCK;

    private final JdbcOperations jdbc;
    private final Clock clock;

    PrivacySessionPurgeAdapter(JdbcOperations jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PurgeOutcome purgeEverySession() {
        Instant now = clock.instant();
        // 1) Phiên thật. Đây là câu duy nhất thực sự đẩy người dùng ra ngoài.
        int httpSessions = jdbc.update("DELETE FROM spring_session");
        // 2) Bản sao để hiển thị — đồng bộ lại để màn "thiết bị đang đăng nhập" không nói sai.
        int deviceSessions = jdbc.update("""
                UPDATE user_device_session
                   SET revoked_at = ?, revoke_reason = ?
                 WHERE revoked_at IS NULL
                """, now, REVOKE_REASON.name());
        // 3) OTP đang mở + ticket chưa dùng. KHÔNG xoá dòng và KHÔNG chạm verified_at/ticket_hash:
        //    ck_email_otp_verified_pair buộc (verified_at IS NULL) = (ticket_hash IS NULL), và các
        //    dòng này còn là bằng chứng điều tra brute-force (p15 §15.5.1 "giữ 24 giờ").
        int otp = jdbc.update("""
                UPDATE email_otp
                   SET consumed_at = COALESCE(consumed_at, ?),
                       ticket_expires_at = CASE WHEN ticket_hash IS NULL
                                                THEN ticket_expires_at ELSE ? END
                 WHERE consumed_at IS NULL
                    OR (ticket_hash IS NOT NULL
                        AND (ticket_expires_at IS NULL OR ticket_expires_at > ?))
                """, now, now, now);
        return new PurgeOutcome(httpSessions, deviceSessions, otp);
    }
}
