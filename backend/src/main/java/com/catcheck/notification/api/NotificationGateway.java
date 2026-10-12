package com.catcheck.notification.api;

import java.util.UUID;

public interface NotificationGateway {

    void enqueue(NotificationRequest request);

    void enqueueTransactionalEmail(TransactionalEmailRequest request);

    int revokePushOnConsentWithdrawal(UUID userId);
}
