package com.catcheck.notification.infrastructure.push;

import com.catcheck.notification.domain.PushPayload;
import com.catcheck.notification.domain.PushTarget;
import com.catcheck.notification.domain.port.PushMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
