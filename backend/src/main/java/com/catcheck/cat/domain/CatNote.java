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

    @Column(name = "occurred_on")
    private LocalDate occurredOn;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CatNote() {
    }

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
