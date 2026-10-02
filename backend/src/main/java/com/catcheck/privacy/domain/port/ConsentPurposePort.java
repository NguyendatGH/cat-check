package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.ConsentPurpose;

import java.util.List;
import java.util.Optional;

/**
 * Cổng đọc danh mục {@code consent_purpose} (p4 B3). Chỉ đọc — danh mục do admin/DPO
 * sửa bằng công cụ quản trị, không sửa qua API nghiệp vụ M1.
 */
public interface ConsentPurposePort {

    /** Mọi mục đích {@code active = true}, sắp theo {@code (phase, display_order)} — render Trung tâm quyền riêng tư một lần. */
    List<ConsentPurpose> findAllActive();

    /** Tra theo mã — ném lỗi ở tầng service khi không tồn tại (p8 CONSENT_PURPOSE_UNKNOWN). */
    Optional<ConsentPurpose> findByCode(String purposeCode);
}
