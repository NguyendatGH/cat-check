package com.catcheck.privacy.application.privacy;

import com.catcheck.privacy.domain.port.UserActivityLogPort;
import com.catcheck.privacy.spi.ErasureParticipant;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {@link ErasureParticipant} của chính module privacy (R10: phải nằm trong
 * {@code ..application.privacy..}).
 *
 * <p>Phạm vi xoá của module này:
 * <ul>
 *   <li>{@code user_activity_log} — <b>xoá cứng</b> (bắt buộc, p4 B11: bảng rủi ro pháp lý
 *       cao nhất trong nhóm B). Việc này nằm ở {@code ErasureCoordinator} để chạy TRƯỚC
 *       mọi participant khác.</li>
 *   <li>{@code consent_record} — <b>GIỮ</b> (p15 §15.4.6): user_id sang
 *       {@code pseudonym_id} sau khi tài khoản được ẩn danh hoá, giữ thêm 5 năm. Việc ánh
 *       xạ sang tombstone nằm ở tầng ẩn danh hoá của identity (D+7) — xem
 *       {@code docs/handovers/A2.md}.</li>
 *   <li>{@code dsar_request} — <b>giữ</b> dạng ẩn danh 5 năm; {@code security_incident} —
 *       giữ ≥ 5 năm (Đ29.1.c). Không xoá trong mọi trường hợp.</li>
 * </ul></p>
 */
@Component
public class PrivacyErasureParticipant implements ErasureParticipant {

    public static final String NAME = "privacy";

    private final UserActivityLogPort activityLogPort;

    public PrivacyErasureParticipant(UserActivityLogPort activityLogPort) {
        this.activityLogPort = activityLogPort;
    }

    @Override
    public String participantName() {
        return NAME;
    }

    /**
     * Xoá cứng log hành vi của subject. Gọi kÉM các bảng bằng chứng (consent_record,
     * dsar_request, security_incident) không bị đụng — chúng giữ theo vòng đời riêng.
     */
    @Override
    public void erase(UUID subjectId) {
        activityLogPort.deleteByUser(subjectId);
    }
}
