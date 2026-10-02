package com.catcheck.scan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Ảnh gốc của một lần quét — ánh xạ {@code scan_image} (p4 D2).
 *
 * <p>Bất biến I7 (trigger V16, W3): {@code scan.store_image = false} ⇒ không tồn tại dòng nào ở
 * đây. Tầng application của module này PHẢI tôn trọng bất biến đó khi tạo dòng (chỉ tạo khi
 * {@link Scan#isStoreImage()}).</p>
 */
@Entity
@Table(name = "scan_image")
public class ScanImage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "scan_id", nullable = false, updatable = false)
    private UUID scanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 16)
    private ScanImageStorageProvider storageProvider;

    @Column(name = "storage_key", columnDefinition = "text")
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "bytes", nullable = false)
    private long bytes;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    // VARCHAR(64) khong phai CHAR(64) (xem docs/handovers/A6.md): Hibernate luon suy ra VARCHAR
    // cho truong String khi validate schema bat ke columnDefinition ghi gi - CHAR(64) o migration
    // se luon bao "wrong column type" du entity khai the nao (xac nhan bang Testcontainers that,
    // thu ca @Column(columnDefinition="char(64)") lan khong). SHA-256 hex luon dung 64 ky tu nen
    // VARCHAR(64) tuong duong CHAR(64) ve mat du lieu (Postgres cung khuyen dung varchar/text hon
    // char(n) - xem tai lieu kieu ky tu cua Postgres).
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delete_reason", length = 24)
    private ScanImageDeleteReason deleteReason;

    @Column(name = "migrated_at")
    private Instant migratedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScanImage() {
    }

    public ScanImage(UUID id, UUID scanId, ScanImageStorageProvider storageProvider, String storageKey,
                      String contentType, long bytes, int width, int height, String checksumSha256,
                      Instant expiresAt, Instant now) {
        this.id = id;
        this.scanId = scanId;
        this.storageProvider = storageProvider;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.bytes = bytes;
        this.width = width;
        this.height = height;
        this.checksumSha256 = checksumSha256;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getScanId() {
        return scanId;
    }

    public ScanImageStorageProvider getStorageProvider() {
        return storageProvider;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getBytes() {
        return bytes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public ScanImageDeleteReason getDeleteReason() {
        return deleteReason;
    }

    public Instant getMigratedAt() {
        return migratedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isExpired(Instant now) {
        return deletedAt != null || expiresAt.isBefore(now);
    }

    public void markDeleted(ScanImageDeleteReason reason, Instant now) {
        this.deletedAt = now;
        this.deleteReason = reason;
        this.storageKey = null;
        this.updatedAt = now;
    }
}
