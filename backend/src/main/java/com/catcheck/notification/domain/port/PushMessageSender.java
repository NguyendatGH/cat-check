package com.catcheck.notification.domain.port;

import com.catcheck.notification.domain.PushPayload;
import com.catcheck.notification.domain.PushTarget;

import java.util.List;
import java.util.UUID;

/**
 * Cổng gửi push. Hiện thực thật là FCM ({@code firebase-admin}); khi thiếu credential thì có
 * một hiện thực vô hiệu hoá trả toàn bộ {@link Status#SKIPPED} để outbox giữ nguyên trạng thái
 * chờ (research §0 nguyên tắc 5: mọi tích hợp ngoài phải có abstraction).
 */
public interface PushMessageSender {

    /** Có gửi được không — {@code false} khi chưa cấu hình credential. */
    boolean enabled();

    /**
     * Gửi multicast (p12 §12.3.2: KHÔNG loop gửi tuần tự). Kết quả trả theo đúng
     * {@code subscriptionId} của từng đích, không dựa vào thứ tự list.
     */
    List<Result> send(List<PushTarget> targets, PushPayload payload);

    /** Kết quả cho một đích. */
    record Result(UUID subscriptionId, Status status, String messageId, String errorCode, String errorMessage) {

        public static Result delivered(UUID subscriptionId, String messageId) {
            return new Result(subscriptionId, Status.DELIVERED, messageId, null, null);
        }

        public static Result skipped(UUID subscriptionId, String reason) {
            return new Result(subscriptionId, Status.SKIPPED, null, null, reason);
        }
    }

    /** Phân loại kết quả theo p12 §12.3.9. */
    enum Status {
        /** FCM nhận. */
        DELIVERED,
        /** {@code UNREGISTERED}/{@code INVALID_ARGUMENT} — thu hồi subscription NGAY, không retry. */
        PERMANENT_FAILURE,
        /** {@code UNAVAILABLE}/{@code INTERNAL}/{@code QUOTA_EXCEEDED} — tăng failure_count, backoff. */
        TRANSIENT_FAILURE,
        /** Không gửi vì push chưa được cấu hình — <b>không</b> tính là một lần thử. */
        SKIPPED
    }
}
