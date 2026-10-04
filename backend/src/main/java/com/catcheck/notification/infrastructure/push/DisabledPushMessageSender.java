package com.catcheck.notification.infrastructure.push;

import com.catcheck.notification.domain.PushPayload;
import com.catcheck.notification.domain.PushTarget;
import com.catcheck.notification.domain.port.PushMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hiện thực dùng khi <b>chưa cấu hình credential Firebase</b>
 * ({@code FIREBASE_SERVICE_ACCOUNT_PATH} rỗng — đúng tình trạng của máy dev và của CI).
 *
 * <p>Mục tiêu là "vô hiệu hoá CÓ KIỂM SOÁT": app vẫn khởi động, endpoint G9–G11 vẫn chạy,
 * {@code notification_outbox} vẫn được ghi; chỉ bước đẩy đi là không xảy ra và các dòng giữ
 * nguyên {@code PENDING}. Trả {@link Status#SKIPPED} chứ KHÔNG trả thất bại, vì thất bại sẽ
 * tiêu {@code attempts} và sau 5 vòng job toàn bộ push rơi vào dead letter chỉ vì môi trường
 * thiếu khoá.</p>
 *
 * <p>Cảnh báo chỉ log MỘT lần cho mỗi lần khởi động: job chạy mỗi 15 phút, log mỗi vòng sẽ
 * thành nhiễu.</p>
 */
class DisabledPushMessageSender implements PushMessageSender {

    private static final Logger log = LoggerFactory.getLogger(DisabledPushMessageSender.class);

    private final AtomicBoolean warned = new AtomicBoolean(false);

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public List<Result> send(List<PushTarget> targets, PushPayload payload) {
        if (warned.compareAndSet(false, true)) {
            log.warn("FCM chưa được cấu hình: thiếu catcheck.notification.push.service-account-path "
                    + "(FIREBASE_SERVICE_ACCOUNT_PATH). Push bị vô hiệu hoá; notification_outbox giữ "
                    + "nguyên PENDING và sẽ được đẩy khi cấu hình xong. Thông báo in-app không bị ảnh hưởng.");
        }
        return targets.stream()
                .map(target -> Result.skipped(target.subscriptionId(), "PUSH_NOT_CONFIGURED"))
                .toList();
    }
}
