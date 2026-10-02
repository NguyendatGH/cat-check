package com.catcheck.audit.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mot dong audit se ghi. Dung {@link #builder()} de tao — 11 truong neu danh sach
 * vi tri thi de sai thu tu.
 *
 * <p>Chua {@code occurredAt}: thoi diem do {@code Clock} cua he thong quyet dinh
 * (R13 — khong bao gio goi {@code Instant.now()}).</p>
 *
 * <p>KHONG dua PII tho vao {@code metadata}/{@code before}/{@code after}. Tang
 * application conap {@code PiiRedactor} loai khoa/gi tri nhay cam truoc khi ghi
 * (p11 §11.10.3), nhung nguyen tac la khong truyen PII vao day (p4 §4.6.4).</p>
 */
public record AuditEvent(
        AuditActor actor,
        AuditSubjectType subjectType,
        UUID subjectUserId,
        String action,
        AuditOutcome outcome,
        Map<String, Object> metadata,
        Map<String, Object> before,
        Map<String, Object> after,
        String requestId,
        String ipAddress,
        String userAgent) {

    public AuditEvent {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action bat buoc");
        }
        if (subjectType == null) {
            throw new IllegalArgumentException("subject_type bat buoc");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("result bat buoc");
        }
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        before = before == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(before));
        after = after == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(after));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private AuditActor actor = AuditActor.system();
        private AuditSubjectType subjectType = AuditSubjectType.SYSTEM;
        private UUID subjectUserId;
        private String action;
        private AuditOutcome outcome = AuditOutcome.SUCCESS;
        private Map<String, Object> metadata = Map.of();
        private Map<String, Object> before;
        private Map<String, Object> after;
        private String requestId;
        private String ipAddress;
        private String userAgent;

        private Builder() {
        }

        public Builder actor(AuditActor value) {
            this.actor = value;
            return this;
        }

        public Builder subject(AuditSubjectType type, UUID userId) {
            this.subjectType = type;
            this.subjectUserId = userId;
            return this;
        }

        public Builder subjectUser(UUID userId) {
            this.subjectType = AuditSubjectType.USER;
            this.subjectUserId = userId;
            return this;
        }

        public Builder action(String value) {
            this.action = value;
            return this;
        }

        public Builder outcome(AuditOutcome value) {
            this.outcome = value;
            return this;
        }

        public Builder denied() {
            this.outcome = AuditOutcome.DENIED;
            return this;
        }

        public Builder error() {
            this.outcome = AuditOutcome.ERROR;
            return this;
        }

        public Builder metadata(Map<String, Object> value) {
            this.metadata = value == null ? Map.of() : value;
            return this;
        }

        public Builder meta(String key, Object value) {
            this.metadata = new LinkedHashMap<>(metadata);
            this.metadata.put(key, value);
            return this;
        }

        public Builder before(Map<String, Object> value) {
            this.before = value;
            return this;
        }

        public Builder after(Map<String, Object> value) {
            this.after = value;
            return this;
        }

        public Builder requestId(String value) {
            this.requestId = value;
            return this;
        }

        public Builder ipAddress(String value) {
            this.ipAddress = value;
            return this;
        }

        public Builder userAgent(String value) {
            this.userAgent = value;
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(actor, subjectType, subjectUserId, action, outcome,
                    metadata, before, after, requestId, ipAddress, userAgent);
        }
    }
}
