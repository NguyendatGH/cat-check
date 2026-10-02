package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.PolicyType;
import com.catcheck.privacy.domain.PolicyVersion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng quản lý phiên bản văn bản chính sách (p4 B1).
 *
 * <p>Không có phương thức sửa: một version đã có {@code consent_record} trỏ tới thì
 * <b>không bao giờ được sửa</b> — sửa nội dung = publish version mới (p15 REQ-VER-07).</p>
 */
public interface PolicyVersionPort {

    /** INSERT phiên bản mới. Trùng {@code (policy_type, version, locale)} ⇒ lỗi DB. */
    void publish(PolicyVersion version);

    /** Bản đang hiệu lực của một loại tài liệu tại {@code now} — dùng cổng consent (p15 REQ-VER-03) và công khai (C16). */
    Optional<PolicyVersion> findCurrent(PolicyType type, String locale, Instant now);

    Optional<PolicyVersion> findById(UUID id);

    /** Mọi bản của một loại tài liệu, mới nhất trước — dùng so sánh khi publish. */
    List<PolicyVersion> findAllByType(PolicyType type, String locale);

    /**
     * Một phiên bản CỤ THỂ theo semver — nền của permalink F10 (p15 REQ-LEGAL-03).
     *
     * <p>KHÁC {@link #findCurrent}: cố tình KHÔNG lọc theo {@code effective_from/effective_to}.
     * Permalink phải sống vĩnh viễn, nên một bản đã bị thay thế vẫn phải trả về được —
     * {@code consent_record.policy_version} trỏ thẳng vào URL này để chứng minh user đã
     * đồng ý với nội dung nào.</p>
     */
    Optional<PolicyVersion> findByTypeAndVersion(PolicyType type, String version, String locale);
}
