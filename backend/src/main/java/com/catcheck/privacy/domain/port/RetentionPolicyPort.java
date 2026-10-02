package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.RetentionPolicy;

import java.util.List;
import java.util.Optional;

/**
 * Cổng quản lý {@code retention_policy} (p4 B6) — thời hạn lưu là CẤU HÌNH, chỉ DPO
 * được sửa (p11 §15.5.2 REQ-RET-06).
 */
public interface RetentionPolicyPort {

    List<RetentionPolicy> findAll();

    Optional<RetentionPolicy> findByCode(String code);

    /** Thêm mới hoặc cập nhật (theo {@code code}). */
    void save(RetentionPolicy policy);
}
