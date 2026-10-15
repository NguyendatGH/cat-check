package com.catcheck.notification.api;

import java.util.UUID;

public interface NotificationGateway {

    /** @return {@code true} khi đã ghi ít nhất một dòng {@code notification} mới. */
    boolean enqueue(NotificationRequest request);

    void enqueueTransactionalEmail(TransactionalEmailRequest request);

    int revokePushOnConsentWithdrawal(UUID userId);
}
