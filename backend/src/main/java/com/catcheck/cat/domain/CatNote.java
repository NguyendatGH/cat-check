package com.catcheck.cat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Ghi chú của chủ nuôi về một mèo (p4 §4.4.4, bảng {@code cat_note}).
 *
 * <p><b>Ghi chú là quan sát, không phải chẩn đoán.</b> Cột {@code body} là văn bản tự do của người
 * dùng và đi thẳng vào hồ sơ PDF mà bác sĩ đọc, nên tầng application phải chặn chữ "chẩn đoán"
 * và không được tự sinh kết luận y tế nào từ câu chữ này (p4 C4, quyết định #6).</p>
 *
 * <p><b>Không có {@code scan_id} ở Phase 1.</b> p4 §4.9.2 đặt cột đó ở V11, còn V8 chỉ tạo bốn
 * bảng lõi — thêm cột ở đây sẽ khiến {@code ddl-auto=validate} fail với DB đã migrate tới V11
 * theo kế hoạch.</p>
 */
@Entity
@Table(name = "cat_note")
public class CatNote {

    public static final int BODY_MAX_LENGTH = 2000;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "cat_id", nullable = false)
    private UUID catId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 24)
    private NoteType noteType = NoteType.GENERAL;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    /** Ngày sự việc xảy ra — có thể trong quá khứ, không được ở tương lai. */
    @Column(name = "occurred_on")
    private LocalDate occurredOn;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Bắt buộc cho JPA. */
    protected CatNote() {
    }

    /**
     * D14 — tạo ghi chú. {@code authorUserId} lấy từ phiên đăng nhập, không lấy từ body request.
     */
    public static CatNote create(UUID id, UUID catId, UUID authorUserId, NoteType noteType,
                                String body, LocalDate occurredOn, LocalDate today, Instant now) {
        Objects.requireNonNull(id, "cat_note.id phải có giá trị");
        Objects.requireNonNull(catId, "cat_note.catId phải có giá trị");
        Objects.requireNonNull(authorUserId, "cat_note.authorUserId phải có giá trị");
        Objects.requireNonNull(today, "cat_note.today phải có giá trị - domain không tự đọc đồng hồ hệ thống");
        CatNote note = new CatNote();
        note.id = id;
        note.catId = catId;
        note.authorUserId = authorUserId;
        note.noteType = noteType == null ? NoteType.GENERAL : noteType;
        note.createdAt = now;
        note.updatedAt = now;
        note.applyBody(body);
        note.applyOccurredOn(occurredOn, today);
        return note;
    }

    /**
     * D18 (p8 §8.4.4 — {@code PATCH /cat-notes/{id}}) — merge-patch. p8 chốt cả ba trường
     * {@code noteType?, body?, occurredOn?} là có thể sửa; đây là part sở hữu danh mục endpoint
     * (spec/04-index.md §2) nên thắng so với bản đầu của A3 (chỉ cho sửa {@code body}) — javadoc
     * cũ ở đây bị xoá vì lý do "đổi loại ghi chú làm sai nghĩa lọc" không phải bất biến p4 nào cụ
     * thể, chỉ là suy đoán chưa đối chiếu.
     *
     * <p>Theo đúng quy ước merge-patch dùng xuyên domain này (xem {@link com.catcheck.cat.domain.Cat#update}):
     * tham số {@code null} nghĩa là giữ nguyên, nên không có cách "xoá" {@code occurredOn} bằng
     * PATCH — nhất quán với việc {@code cat_note.occurred_on} vốn đã có thể để trống ngay từ lúc
     * tạo (D17) nếu chủ không nhớ ngày.</p>
     */
    public void update(NoteType noteType, String body, LocalDate occurredOn, LocalDate today, Instant now) {
        if (noteType != null) {
            this.noteType = noteType;
        }
        if (body != null) {
            applyBody(body);
        }
        if (occurredOn != null) {
            applyOccurredOn(occurredOn, today);
        }
        this.updatedAt = now;
    }

    /** D16 — xoá mềm, giữ lại để hồ sơ PDF đã xuất không đứt liên kết. */
    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCatId() {
        return catId;
    }

    public UUID getAuthorUserId() {
        return authorUserId;
    }

    public NoteType getNoteType() {
        return noteType;
    }

    public String getBody() {
        return body;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void applyBody(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("cat_note.body không được rỗng");
        }
        String trimmed = value.trim();
        if (trimmed.length() > BODY_MAX_LENGTH) {
            throw new IllegalArgumentException("cat_note.body dài tối đa " + BODY_MAX_LENGTH + " ký tự");
        }
        this.body = trimmed;
    }

    private void applyOccurredOn(LocalDate value, LocalDate today) {
        if (value != null && value.isAfter(today)) {
            throw new IllegalArgumentException("cat_note.occurred_on không được ở tương lai");
        }
        this.occurredOn = value;
    }
}
