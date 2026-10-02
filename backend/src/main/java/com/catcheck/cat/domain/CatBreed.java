package com.catcheck.cat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Một giống mèo trong danh mục {@code cat_breed} (p4 C2).
 *
 * <p>Khoá tự nhiên là {@link #code} dạng {@code VARCHAR(48)} — p4 §4.1.1 xếp {@code cat_breed} vào
 * nhóm bảng cấu hình có khoá tự nhiên, nên KHÔNG dùng UUID khoá chính.</p>
 *
 * <p>Tên hiển thị lưu song ngữ ở hai cột {@code name_vi}/{@code name_en} (p4 §4.1.7): mèo ở Việt
 * Nam thấy nhãn tiếng Việt, và nhãn không đổi theo {@code Accept-Language} của client — còn các
 * câu chữ trong bài viết thì đổi theo locale, nên hai loại nhãn này phải tách.</p>
 *
 * <p>Entity này chỉ phục vụ đọc danh mục; {@code code} là hằng số trong mã nguồn sau khi seed ở V7
 * nên không có API ghi trong Phase 1 (p14 §14.4 chỉ mở API quản trị cho bảng màu, không phải giống).</p>
 */
@Entity
@Table(name = "cat_breed")
public class CatBreed {

    @Id
    @Column(name = "code", nullable = false, length = 48)
    private String code;

    @Column(name = "name_vi", nullable = false, columnDefinition = "text")
    private String nameVi;

    @Column(name = "name_en", nullable = false, columnDefinition = "text")
    private String nameEn;

    /** Hiện ở chip "gợi ý nhanh" trên màn hồ sơ mèo (p4 C2, p9 M1 01c-3). */
    @Column(name = "popular", nullable = false)
    private boolean popular;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Bắt buộc cho JPA. */
    protected CatBreed() {
    }

    public CatBreed(String code, String nameVi, String nameEn, boolean popular, int sortOrder,
                    boolean active, Instant createdAt, Instant updatedAt) {
        this.code = code;
        this.nameVi = nameVi;
        this.nameEn = nameEn;
        this.popular = popular;
        this.sortOrder = sortOrder;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getCode() {
        return code;
    }

    public String getNameVi() {
        return nameVi;
    }

    public String getNameEn() {
        return nameEn;
    }

    public boolean isPopular() {
        return popular;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
