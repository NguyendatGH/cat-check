package com.catcheck.notification.api;

import java.util.UUID;

public interface OutboxAdminGateway {

    ResendOutcome resendFailed(UUID outboxId);

    enum ResendOutcome {

        REQUEUED,

        NOT_FOUND,

        NOT_FAILED
    }
}
