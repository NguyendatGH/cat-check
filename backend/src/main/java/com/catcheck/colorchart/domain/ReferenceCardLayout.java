package com.catcheck.colorchart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Layout thẻ màu tham chiếu — ánh xạ 1-1 với bảng {@code reference_card_layout} (p4 D8).
 *
 * <p>{@code layout} là JSONB: kích thước vật lý, canonical px, marker (ArUco/QR), danh sách patch
 * với {@code rect}/{@code ref_srgb}/{@code ref_lab}. JSONB đúng chỗ — cấu trúc lồng sâu, chỉ đọc
 * nguyên khối khi khởi tạo detector, không bao giờ lọc theo trường con.
 */
@Entity
@Table(name = "reference_card_layout")
public class ReferenceCardLayout {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "version", nullable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "layout", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> layout;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReferenceCardLayout() {
    }

    public ReferenceCardLayout(UUID id, String code, int version, Map<String, Object> layout,
                               boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.version = version;
        this.layout = layout;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getVersion() {
        return version;
    }

    public Map<String, Object> getLayout() {
        return layout;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void markUpdated(Instant now) {
        this.updatedAt = now;
    }
}
