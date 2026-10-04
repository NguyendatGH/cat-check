package com.catcheck.notification.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nội dung một push.
 *
 * <p>p12 §12.3.6 bắt buộc payload dạng <b>{@code data} thuần</b>, KHÔNG dùng {@code notification}
 * payload của FCM: service worker tự kiểm soát việc hiển thị, tránh rò nội dung nhạy cảm ở bước
 * render mặc định của trình duyệt. Nội dung cũng phải tuân p12 §12.2.1 — banner không được nêu
 * giá trị pH, tên phân loại hay mức độ bất thường.</p>
 */
public record PushPayload(String title, String body, String deepLink, Map<String, String> data) {

    public PushPayload {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    /** Toàn bộ cặp key/value sẽ nằm trong {@code data} của message FCM. */
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
