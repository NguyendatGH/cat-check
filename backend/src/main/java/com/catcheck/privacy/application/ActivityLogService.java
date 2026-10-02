package com.catcheck.privacy.application;

import com.catcheck.privacy.domain.UserActivityLog;
import com.catcheck.privacy.domain.port.ConsentRecordPort;
import com.catcheck.privacy.domain.port.UserActivityLogPort;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.privacy.spi.UserAccountSnapshot;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Ghi log hành vi sản phẩm — {@code user_activity_log} (p4 B11).
 *
 * <p><b>Đây là bảng rủi ro pháp lý cao nhất trong nhóm B</b> (p15 §15.2.4): "lịch sử dùng
 * app" có thể bị coi là dữ liệu cá nhân nhạy cảm. Hệ quả bắt buộc (p15 REQ-PRIV-02):
 * chỉ ghi khi user đồng ý {@code PRODUCT_ANALYTICS} (mặc định TẮT), không ghi khi tài
 * khoản RESTRICTED, xoá cứng khi xoá tài khoản. {@code props} không chứa PII, không
 * chứa giá trị pH (I29).</p>
 */
@Service
public class ActivityLogService {

    public static final String PURPOSE_PRODUCT_ANALYTICS = "PRODUCT_ANALYTICS";

    private final UserActivityLogPort logPort;
    private final ConsentRecordPort consentPort;
    private final UserAccountPort userAccountPort;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public ActivityLogService(
            UserActivityLogPort logPort,
            ConsentRecordPort consentPort,
            UserAccountPort userAccountPort,
            UuidV7 uuidV7,
            Clock clock
    ) {
        this.logPort = logPort;
        this.consentPort = consentPort;
        this.userAccountPort = userAccountPort;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /**
     * Ghi một sự kiện hành vi. Cổng consent (p15 REQ-PRIV-02): chỉ ghi khi user đã
     * đồng ý {@code PRODUCT_ANALYTICS}; tài khoản RESTRICTED thì im lặng bỏ qua (không
     * lỗi — đây là nghĩa vụ pháp lý, không phải lỗi của caller). Trả về {@code false}
     * khi BỎ QUA vì chưa consent.
     */
    @Transactional
    public boolean record(UUID userId, String eventCode, Map<String, Object> props) {
        UserAccountSnapshot snapshot = userAccountPort.snapshot(userId);
        if (snapshot.isProcessingRestricted() || snapshot.isAnonymized()) {
            return false;
        }
        boolean granted = consentPort.findLatest(userId, PURPOSE_PRODUCT_ANALYTICS)
                .map(record -> record.status() == com.catcheck.privacy.domain.ConsentStatus.GRANTED)
                .orElse(false);
        if (!granted) {
            return false;
        }
        Instant now = clock.instant();
        logPort.append(new UserActivityLog(uuidV7.generate(), userId, eventCode, props, now, now));
        return true;
    }

    /** Xoá cứng toàn bộ log hành vi của user — bắt buộc khi xoá tài khoản (p4 B11, p15 §15.4.6). */
    @Transactional
    public int eraseByUser(UUID userId) {
        return logPort.deleteByUser(userId);
    }
}
