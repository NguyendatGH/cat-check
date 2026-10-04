package com.catcheck.notification.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Một thiết bị đang nhận push của tôi (p8 §8.4.7 G9).
 *
 * <p><b>Cố ý KHÔNG trả {@code fid} / {@code legacyToken}.</b> Hai cột đó nằm trong phạm vi
 * {@code ReencryptPiiColumnsJob} (p12 §12.6.4a liệt kê {@code push_subscription.fid} và
 * {@code legacy_token} là cột PII) nên không được đưa ra ngoài; client vốn tự biết FID của
 * chính nó, còn FID của máy khác thì không việc gì phải biết. {@code deviceLabel} đã đủ để user
 * nhận ra máy của mình (p4 F3).</p>
 */
public record PushSubscriptionView(
        UUID id,
        String platform,
        String deviceLabel,
        Instant lastSeenAt,
        Instant lastSuccessAt,
        Instant createdAt) {
}
