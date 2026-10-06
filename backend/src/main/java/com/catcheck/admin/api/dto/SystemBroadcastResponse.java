package com.catcheck.admin.api.dto;

import com.catcheck.notification.api.SystemBroadcastGateway;

/**
 * Ket qua L72.
 *
 * @param recipientsMatched   so chu the du dieu kien nhan
 * @param notificationsQueued so ban ghi thuc su da ghi — {@code 0} khi {@code dryRun = true}.
 *                            Hai con so nay tach nhau chinh la cach doc ket qua mot lan dry-run
 * @param dedupeKeyPrefix     tien to khoa chong trung (p12 §12.4). Tra ve de mot lan bam lap lai
 *                            truy nguoc duoc: cung tien to = cung lan phat
 */
public record SystemBroadcastResponse(
        String templateCode,
        Boolean dryRun,
        Integer recipientsMatched,
        Integer notificationsQueued,
        String dedupeKeyPrefix) {

    public static SystemBroadcastResponse from(
            String templateCode, boolean dryRun, SystemBroadcastGateway.BroadcastResult result) {
        return new SystemBroadcastResponse(templateCode, dryRun,
                result.recipientsMatched(), result.notificationsQueued(), result.dedupeKeyPrefix());
    }
}
