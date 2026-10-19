package com.catcheck.notification.api;

import java.util.Locale;
import java.util.UUID;

public interface NotificationGateway {

    /** @return {@code true} khi đã ghi ít nhất một dòng {@code notification} mới. */
    boolean enqueue(NotificationRequest request);

    void enqueueTransactionalEmail(TransactionalEmailRequest request);

    /** Locale đã lưu của người nhận ({@code app_user.locale}); mặc định {@code vi}. */
    Locale recipientLocale(UUID userId);

    int revokePushOnConsentWithdrawal(UUID userId);
}
