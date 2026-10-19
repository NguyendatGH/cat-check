package com.catcheck.notification.api;

import java.util.Map;

public record TransactionalEmailRequest(
        String toAddress,
        String templateCode,
        String locale,
        Map<String, Object> payload,
        String dedupeKey) {

    public TransactionalEmailRequest {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        locale = (locale == null || locale.isBlank()) ? "vi" : locale;
    }
}
