package com.catcheck.admin.api.dto;

import com.catcheck.notification.api.SystemBroadcastGateway;

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
