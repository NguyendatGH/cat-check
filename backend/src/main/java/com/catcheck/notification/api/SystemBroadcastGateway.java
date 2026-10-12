package com.catcheck.notification.api;

import java.util.Map;
import java.util.Set;

public interface SystemBroadcastGateway {

    Set<String> ALLOWED_TEMPLATE_CODES = Set.of("SYSTEM_MAINTENANCE", "PRIVACY_INCIDENT_NOTICE");

    BroadcastResult broadcast(String templateCode, String title, String body,
                              Map<String, Object> payload, boolean dryRun);

    record BroadcastResult(int recipientsMatched, int notificationsQueued, String dedupeKeyPrefix) {
    }
}
