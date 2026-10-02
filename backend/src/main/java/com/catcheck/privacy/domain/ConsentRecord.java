package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Một bản ghi đồng ý — dòng {@code consent_record} (p4 B2).
 *
 * <p><b>Append-only ở tầng DB</b> (bất biến I16): REVOKE UPDATE, DELETE với role ứng dụng.
 * Rút đồng ý = INSERT dòng mới {@code WITHDRAWN} + {@code supersedes_id}, không bao giờ sửa
 * dòng cũ. {@code policy_hash} + {@code consent_text_hash} là cốt lõi nghĩa vụ chứng minh
 * Điều 6.2 NĐ356.</p>
 *
 * @param id               UUID v7 sinh ở tầng ứng dụng
 * @param userId           chủ thể dữ liệu — FK app_user(id) ON DELETE RESTRICT: xoá tài
 *                         khoản phải ẩn danh hoá sang user tombstone, không xoá bằng chứng
 * @param purposeCode      mục đích xử lý cụ thể — FK consent_purpose(code)
 * @param status           GRANTED / DENIED / WITHDRAWN
 * @param policyVersionId  bản chính sách đang hiệu lực lúc đó — FK policy_version(id)
 * @param policyHash        snapshot policy_version.content_hash — chứng minh nội dung đã đọc
 * @param consentTextHash   hash của ĐÚNG chuỗi text cạnh checkbox lúc đó (khác policyHash:
 *                          user đọc câu ngắn cạnh ô tick, không đọc cả văn bản)
 * @param method           kênh ghi nhận (WEB_CHECKBOX/WEB_TOGGLE/…)
 * @param uiSurface        'register', 'scan_first_run', 'privacy_center'…
 * @param locale           ngôn ngữ của văn bản user thực sự đọc ('vi'/'en')
 * @param supersedesId     dòng bị thay thế bởi dòng này, null khi là dòng đầu tiên
 * @param occurredAt       thời điểm hành động (bằng chứng)
 * @param ipAddress        IP lúc hành động — giữ lâu hơn log thường (vòng đời tài khoản + 5 năm)
 * @param userAgent        User-Agent lúc hành động
 * @param requestId        nối với audit_log.request_id
 * @param evidence         ngữ cảnh bổ sung để tái dựng màn hình (JSONB)
 * @param createdAt        mốc ghi dòng
 */
public record ConsentRecord(
        UUID id,
        UUID userId,
        String purposeCode,
        ConsentStatus status,
        UUID policyVersionId,
        String policyHash,
        String consentTextHash,
        ConsentMethod method,
        String uiSurface,
        String locale,
        UUID supersedesId,
        Instant occurredAt,
        String ipAddress,
        String userAgent,
        String requestId,
        Map<String, Object> evidence,
        Instant createdAt
) {

    public ConsentRecord {
        if (id == null || userId == null || purposeCode == null || status == null) {
            throw new IllegalArgumentException("consentRecord thiếu trường bắt buộc");
        }
        if (policyVersionId == null || policyHash == null || consentTextHash == null || method == null) {
            throw new IllegalArgumentException(
                    "consentRecord bắt buộc có policyVersionId, policyHash, consentTextHash, method (p15 §15.10-C)");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("consentRecord.occurredAt bắt buộc");
        }
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
