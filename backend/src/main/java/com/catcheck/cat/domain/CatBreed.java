package com.catcheck.cat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

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
