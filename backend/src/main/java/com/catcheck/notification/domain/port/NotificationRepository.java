package com.catcheck.notification.domain.port;

import com.catcheck.notification.domain.Notification;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Optional<Notification> insertIfAbsent(Notification notification);

    Page listInbox(UUID userId, InboxCursor cursor, int limit);

    long countUnread(UUID userId);

    Optional<Notification> findByIdForUser(UUID notificationId, UUID userId);

    boolean markRead(UUID notificationId, UUID userId);

    int markAllRead(UUID userId);

    boolean hide(UUID notificationId, UUID userId);

    void updateStatus(UUID notificationId, String status, String lastError);

    record Page(List<Notification> items, String nextCursor, boolean hasMore) {
    }

    record InboxCursor(Instant createdAt, UUID id) {

        public InboxCursor {
            if (createdAt == null || id == null) {
                throw new IllegalArgumentException("cursor thiếu trường bắt buộc");
            }
        }

        public String encode() {
            String raw = createdAt.toEpochMilli() + "." + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        public static InboxCursor decode(String cursor) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int separator = raw.indexOf('.');
                if (separator <= 0) {
                    throw new IllegalArgumentException("cursor thiếu dấu phân cách");
                }
                return new InboxCursor(Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separator))),
                        UUID.fromString(raw.substring(separator + 1)));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("cursor không hợp lệ", ex);
            }
        }
    }
}
