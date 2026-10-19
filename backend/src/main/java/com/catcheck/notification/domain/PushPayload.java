package com.catcheck.notification.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record PushPayload(String title, String body, String deepLink, Map<String, String> data) {

    public PushPayload {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public Map<String, String> asDataMap() {
        Map<String, String> merged = new LinkedHashMap<>(data);
        if (title != null) {
            merged.put("title", title);
        }
        if (body != null) {
            merged.put("body", body);
        }
        if (deepLink != null) {
            merged.put("url", deepLink);
        }
        return Map.copyOf(merged);
    }
}
