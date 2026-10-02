package com.catcheck.privacy.application;

import com.catcheck.privacy.domain.port.UserActivityLogPort;
import com.catcheck.privacy.spi.ErasureParticipant;
import com.catcheck.privacy.spi.UserAccountPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Điều phối xoá dữ liệu cá nhân xuyên module (p4 §4.6.2, p15 §15.4.6) thông qua SPI
 * {@link ErasureParticipant}: mỗi module có dữ liệu của user implement và tự quyết định
 * cách xoá/ẩn danh hoá phần của mình.
 *
 * <p>Orchestrator chỉ gọi lại — KHÔNG đụng vào bảng của module khác. Gọi lần lượt từng
 * participant trong MỘT transaction (bất biến: một nửa module xoá một nửa không xoá là
 * trạng thái mâu thuẫn). Nếu một participant lỗi ⇒ rollback toàn bộ.</p>
 */
@Service
public class ErasureCoordinator {

    private final List<ErasureParticipant> participants;
    private final UserActivityLogPort activityLogPort;
    private final UserAccountPort userAccountPort;

    public ErasureCoordinator(
            List<ErasureParticipant> participants,
            UserActivityLogPort activityLogPort,
            UserAccountPort userAccountPort
    ) {
        this.participants = participants;
        this.activityLogPort = activityLogPort;
        this.userAccountPort = userAccountPort;
    }

    /**
     * Thực thi xoá toàn bộ dữ liệu cá nhân của {@code subjectId}.
     *
     * <p>Thứ tự: (1) xoá cứng {@code user_activity_log} (bắt buộc, p4 B11); (2) gọi lần
     * lượt các {@link ErasureParticipant} — identity (app_user), cat, scan, credit,… tự
     * lo phần của mình. Log hành vi xoá TRƯỚC vì các participant khác không được ghi vào
     * đó nữa sau khi xoá.</p>
     */
    @Transactional
    public void erase(UUID subjectId) {
        activityLogPort.deleteByUser(subjectId);
        for (ErasureParticipant participant : participants) {
            participant.erase(subjectId);
        }
    }

    /** Số module tham gia xoá — dùng log giám sát tiến trình. */
    public int participantCount() {
        return participants.size();
    }
}
