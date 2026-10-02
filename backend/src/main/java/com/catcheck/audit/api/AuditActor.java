package com.catcheck.audit.api;

import java.util.UUID;

/**
 * Chu the ghi nhat ky.
 *
 * @param type   loai chu the
 * @param userId id nguoi dung thuc hien; {@code null} cho SYSTEM/JOB va cho
 *               kenh khong gan voi nguoi (p4 §4.6.3: khong phai moi actor deu la nguoi dung)
 * @param role   snapshot vai tro tai thoi diem ghi — de audit doc duoc sau khi role
 *               bi thu hoi, khong join {@code user_role} lai
 */
public record AuditActor(AuditActorType type, UUID userId, String role) {

    public AuditActor {
        if (type == null) {
            throw new IllegalArgumentException("actor_type bat buoc");
        }
        if (type == AuditActorType.USER && userId == null) {
            throw new IllegalArgumentException("actor_type=USER thi phai co actor_id");
        }
        if (type == AuditActorType.JOB && userId != null) {
            throw new IllegalArgumentException("actor_type=JOB thi khong co actor_id");
        }
    }

    public static AuditActor user(UUID userId, String role) {
        return new AuditActor(AuditActorType.USER, userId, role);
    }

    public static AuditActor admin(UUID userId, String role) {
        return new AuditActor(AuditActorType.ADMIN, userId, role);
    }

    public static AuditActor dpo(UUID userId, String role) {
        return new AuditActor(AuditActorType.DPO, userId, role);
    }

    public static AuditActor system() {
        return new AuditActor(AuditActorType.SYSTEM, null, null);
    }

    public static AuditActor job() {
        return new AuditActor(AuditActorType.JOB, null, null);
    }
}
