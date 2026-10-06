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

    /**
     * L46 — danh sách phiên bản cho màn quản trị, phân trang kiểu offset (p8 §8.4.12 L46 cột
     * {@code O}). Lọc theo loại/locale là tuỳ chọn; {@code null} = không lọc.
     *
     * <p>Khác {@link #findAllByType}: đó là F9 cho người dùng (một loại tài liệu, một locale),
     * còn đây là mọi loại trong một bảng để DPO soát toàn bộ.</p>
     */
    List<PolicyVersion> findAllForAdmin(PolicyType type, String locale, int limit, int offset);

    /** Tổng số dòng khớp bộ lọc của {@link #findAllForAdmin} — màn admin cần nhảy trang. */
    long countForAdmin(PolicyType type, String locale);

    /**
     * L48 — đánh dấu đã publish: ghi {@code published_by} và chốt {@code effective_from}.
     *
     * <p>Đây là lần UPDATE DUY NHẤT được phép trên bảng này, và nó <b>không chạm nội dung</b>
     * ({@code content_md}/{@code content_hash}): p4 B1 cấm sửa một phiên bản đã có
     * {@code consent_record} trỏ tới, mà bản nháp thì chưa thể có consent nào vì nó chưa từng
     * có hiệu lực. V6 đã {@code REVOKE DELETE} nên không có đường xoá.</p>
     *
     * @return số dòng đã đổi (0 = id không tồn tại)
     */
    int markPublished(UUID id, UUID publishedBy, Instant effectiveFrom);

    /**
     * L48 — chốt {@code effective_to} cho bản đang hiệu lực của cùng {@code (type, locale)}.
     *
     * <p>Không có bước này, hai bản cùng {@code effective_to IS NULL} và
     * {@link #findCurrent} phân xử bằng {@code ORDER BY effective_from DESC} — đúng tình cờ,
     * sai ngay khi ai đó publish một bản có {@code effective_from} trong tương lai.</p>
     *
     * @param exceptId bản vừa publish, không tự đóng chính mình
     * @return số dòng đã đóng
     */
    int closeEffective(PolicyType type, String locale, Instant effectiveTo, UUID exceptId);
}
