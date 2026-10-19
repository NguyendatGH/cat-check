package com.catcheck.notification.domain.port;

import com.catcheck.notification.domain.PushPayload;
import com.catcheck.notification.domain.PushTarget;

import java.util.List;
import java.util.UUID;

public interface PushMessageSender {

    boolean enabled();

    List<Result> send(List<PushTarget> targets, PushPayload payload);

    record Result(UUID subscriptionId, Status status, String messageId, String errorCode, String errorMessage) {

        public static Result delivered(UUID subscriptionId, String messageId) {
            return new Result(subscriptionId, Status.DELIVERED, messageId, null, null);
        }

        public static Result skipped(UUID subscriptionId, String reason) {
            return new Result(subscriptionId, Status.SKIPPED, null, null, reason);
        }
    }

    enum Status {
        DELIVERED,
        PERMANENT_FAILURE,
        TRANSIENT_FAILURE,
        SKIPPED
    }
}
