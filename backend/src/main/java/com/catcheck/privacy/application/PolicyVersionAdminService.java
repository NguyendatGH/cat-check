package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.privacy.api.AdminPolicyErrorCode;
import com.catcheck.privacy.domain.PolicyType;
import com.catcheck.privacy.domain.PolicyVersion;
import com.catcheck.privacy.domain.port.ConsentPurposePort;
import com.catcheck.privacy.domain.port.PolicyVersionPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * L46, L47, L48 — quản trị phiên bản văn bản chính sách (p8 §8.4.12 mục (c)).
 *
 * <h2>"Bản nháp" trong schema này nghĩa là gì</h2>
 * <p>{@code policy_version} (p4 B1) <b>không có cột {@code status}</b> và CLAUDE.md cấm thêm
 * migration ngoài danh mục p4 §4.9.2. Vòng đời vì vậy đọc từ hai cột đã có:</p>
 * <ul>
 *   <li>{@code DRAFT} — {@code published_by IS NULL} và {@code effective_from} còn ở tương lai.
 *       L47 <b>bắt buộc</b> {@code effectiveFrom} ở tương lai, nên một bản nháp không bao giờ
 *       lọt ra endpoint công khai: {@code findCurrent} vốn đã lọc {@code effective_from <= now}.</li>
 *   <li>{@code SCHEDULED} — đã publish nhưng chưa tới ngày hiệu lực (đúng trường hợp bình
 *       thường: p15 REQ-VER-06 đòi báo trước email ≥ 7 ngày).</li>
 *   <li>{@code EFFECTIVE} / {@code SUPERSEDED} — theo cửa sổ {@code effective_from/effective_to}.</li>
 * </ul>
 * <p>Ba dòng seed ({@code R__seed_policy_version.sql}) có {@code published_by = NULL} và
 * {@code effective_from} ở quá khứ, nên chúng hiện {@code EFFECTIVE} chứ không phải
 * {@code DRAFT} — thứ tự kiểm ở {@link #statusOf} cố ý như vậy. Ghi handoff H15.165.</p>
 *
 * <h2>Publish kích hoạt xin lại consent toàn hệ thống — không cần code thêm</h2>
 * <p>{@code PolicyService.pendingReconsentPurposes} đọc {@code findCurrent} rồi so với
 * {@code consent_record} của từng user (p15 REQ-VER-03). Nên một bản
 * {@code requires_reconsent = true} vừa có hiệu lực là đủ để cổng consent chặn mọi user cho tới
 * khi họ xác nhận lại — L48 không tự đi ghi gì vào dữ liệu người dùng. Đó là lý do bước
 * {@link PolicyVersionPort#closeEffective} là bắt buộc, không phải trang trí: thiếu nó thì hai
 * bản cùng {@code effective_to IS NULL} và việc bản nào "hiện hành" phụ thuộc thứ tự sắp xếp.</p>
 */
@Service
public class PolicyVersionAdminService {

    /** p15 REQ-VER-01 — semver {@code MAJOR.MINOR.PATCH}; cột là {@code VARCHAR(16)}. */
    private static final Pattern SEMVER = Pattern.compile("^\\d{1,4}\\.\\d{1,4}(\\.\\d{1,4})?$");

    private final PolicyVersionPort policyPort;
    private final ConsentPurposePort purposePort;
    private final AuditLogService auditLog;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public PolicyVersionAdminService(PolicyVersionPort policyPort,
                                     ConsentPurposePort purposePort,
                                     AuditLogService auditLog,
                                     UuidV7 uuidV7,
                                     Clock clock) {
        this.policyPort = policyPort;
        this.purposePort = purposePort;
        this.auditLog = auditLog;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /** Vòng đời một phiên bản, suy ra từ {@code published_by} + cửa sổ hiệu lực. */
    public enum PolicyVersionStatus {
        DRAFT, SCHEDULED, EFFECTIVE, SUPERSEDED
    }

    /**
     * Một dòng của L46.
     *
     * @param version   phiên bản
     * @param status    vòng đời đã suy ra
     * @param hasContent có {@code content_md} hay chỉ {@code content_url}
     */
    public record AdminPolicyRow(PolicyVersion version, PolicyVersionStatus status, boolean hasContent) {
    }

    /** Trang kết quả của L46 (phân trang offset — p8 §8.4.12 L46 cột {@code O}). */
    public record AdminPolicyPage(List<AdminPolicyRow> rows, int page, int size,
                                  long totalElements, int totalPages, boolean hasMore) {
    }

    // ------------------------------------------------------------------ L46

    @Transactional(readOnly = true)
    public AdminPolicyPage list(String policyType, String locale, int page, int size) {
        PolicyType type = parseTypeFilter(policyType);
        int cappedSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        long total = policyPort.countForAdmin(type, locale);
        List<PolicyVersion> versions =
                policyPort.findAllForAdmin(type, locale, cappedSize, safePage * cappedSize);
        Instant now = clock.instant();
        List<AdminPolicyRow> rows = versions.stream()
                .map(v -> new AdminPolicyRow(v, statusOf(v, now), v.contentMd() != null))
                .toList();
        int totalPages = (int) Math.ceil((double) total / cappedSize);
        return new AdminPolicyPage(rows, safePage, cappedSize, total, totalPages,
                (long) (safePage + 1) * cappedSize < total);
    }

    // ------------------------------------------------------------------ L47

    /**
     * Soạn một phiên bản mới ở trạng thái {@code DRAFT}.
     *
     * @param effectiveFrom phải ở TƯƠNG LAI — nếu cho phép quá khứ thì bản nháp có hiệu lực
     *                      ngay lúc INSERT và bước publish của DPO (L48, có step-up TOTP) bị
     *                      vòng qua hoàn toàn
     */
    @Transactional
    public AdminPolicyRow createDraft(PolicyType type, String version, String locale, String title,
                                      String contentMd, String contentUrl, String summaryOfChanges,
                                      boolean requiresReconsent, List<String> affectedPurposes,
                                      Instant effectiveFrom, PolicyAdminAction action) {
        if (version == null || !SEMVER.matcher(version).matches()) {
            throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID, "version");
        }
        if (contentMd == null && contentUrl == null) {
            throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID, "contentMd");
        }
        Instant now = clock.instant();
        if (effectiveFrom == null || !effectiveFrom.isAfter(now)) {
            throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID, "effectiveFrom");
        }
        if (requiresReconsent && (affectedPurposes == null || affectedPurposes.isEmpty())) {
            // p15 REQ-VER-02: MAJOR bump ⇒ requires_reconsent = true VÀ affected_purposes[]
            // phải điền. Thiếu danh sách thì cổng consent không biết xin lại purpose nào và
            // bản "bắt buộc đồng ý lại" trở thành không có tác dụng gì.
            throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID,
                    "affectedPurposes");
        }
        requirePurposesExist(affectedPurposes);
        if (policyPort.findByTypeAndVersion(type, version, locale).isPresent()) {
            throw new ConflictException(AdminPolicyErrorCode.POLICY_VERSION_EXISTS,
                    type.name(), version, locale);
        }
        String body = contentMd != null ? contentMd : contentUrl;
        PolicyVersion draft = new PolicyVersion(
                uuidV7.generate(), type, version, locale, title, contentMd, contentUrl,
                ContentHashes.sha256Hex(body), summaryOfChanges, requiresReconsent,
                affectedPurposes == null ? List.of() : affectedPurposes,
                effectiveFrom, null, null, now);
        policyPort.publish(draft);
        audit("ADMIN_POLICY_VERSION_DRAFTED", draft, action, Map.of());
        return new AdminPolicyRow(draft, statusOf(draft, now), contentMd != null);
    }

    // ------------------------------------------------------------------ L48

    /**
     * Publish một bản nháp.
     *
     * @param effectiveFrom ghi đè ngày hiệu lực; {@code null} = giữ ngày của bản nháp. Cho ghi
     *                      đè vì khoảng thời gian từ lúc soạn tới lúc rà soát pháp lý xong là
     *                      không đoán được, và p15 REQ-VER-06 đòi báo trước email ≥ 7 ngày tính
     *                      từ ngày này
     */
    @Transactional
    public AdminPolicyRow publish(UUID id, Instant effectiveFrom, PolicyAdminAction action) {
        PolicyVersion draft = policyPort.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        AdminPolicyErrorCode.POLICY_VERSION_NOT_FOUND, id.toString()));
        if (draft.publishedBy() != null) {
            throw new ConflictException(
                    AdminPolicyErrorCode.POLICY_VERSION_ALREADY_PUBLISHED, id.toString());
        }
        Instant now = clock.instant();
        Instant effective = effectiveFrom != null ? effectiveFrom : draft.effectiveFrom();
        if (effective == null) {
            throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID, "effectiveFrom");
        }
        int updated = policyPort.markPublished(id, action.actorId(), effective);
        if (updated == 0) {
            // Ai đó vừa publish xong giữa lúc đọc và lúc ghi. Điều kiện
            // "published_by IS NULL" nằm trong chính câu UPDATE nên chỉ một bên thắng.
            throw new ConflictException(
                    AdminPolicyErrorCode.POLICY_VERSION_ALREADY_PUBLISHED, id.toString());
        }
        int closed = policyPort.closeEffective(draft.policyType(), draft.locale(), effective, id);
        audit("ADMIN_POLICY_VERSION_PUBLISHED", draft, action, Map.of(
                "effectiveFrom", effective.toString(),
                "requiresReconsent", draft.requiresReconsent(),
                "affectedPurposes", draft.affectedPurposes(),
                "supersededCount", closed));
        PolicyVersion published = policyPort.findById(id).orElseThrow();
        return new AdminPolicyRow(published, statusOf(published, now), published.contentMd() != null);
    }

    // ------------------------------------------------------------------ nội bộ

    /**
     * Thứ tự kiểm quan trọng: {@code SUPERSEDED} trước, rồi mới tới cửa sổ tương lai. Đảo lại
     * thì một bản đã bị thay thế nhưng {@code effective_from} tương lai (không xảy ra ở dữ liệu
     * sạch, xảy ra ở dữ liệu nhập tay) sẽ hiện là {@code DRAFT} và admin tưởng sửa được.
     */
    public static PolicyVersionStatus statusOf(PolicyVersion version, Instant now) {
        if (version.effectiveTo() != null && !version.effectiveTo().isAfter(now)) {
            return PolicyVersionStatus.SUPERSEDED;
        }
        if (version.effectiveFrom().isAfter(now)) {
            return version.publishedBy() == null
                    ? PolicyVersionStatus.DRAFT
                    : PolicyVersionStatus.SCHEDULED;
        }
        return PolicyVersionStatus.EFFECTIVE;
    }

    private PolicyType parseTypeFilter(String policyType) {
        if (policyType == null || policyType.isBlank()) {
            return null;
        }
        try {
            return PolicyType.valueOf(policyType.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID, "policyType");
        }
    }

    /**
     * p4 B1: phần tử của {@code affected_purposes} phải tồn tại trong {@code consent_purpose}
     * — "kiểm ở service" vì cột là {@code VARCHAR[]} nên DB không có FK nào làm việc này.
     */
    private void requirePurposesExist(List<String> purposes) {
        if (purposes == null || purposes.isEmpty()) {
            return;
        }
        List<String> known = purposePort.findAllActive().stream()
                .map(com.catcheck.privacy.domain.ConsentPurpose::code)
                .toList();
        for (String purpose : purposes) {
            if (!known.contains(purpose)) {
                throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID,
                        "affectedPurposes." + purpose);
            }
        }
    }

    private void audit(String action, PolicyVersion version, PolicyAdminAction adminAction,
                       Map<String, Object> extra) {
        Map<String, Object> metadata = new LinkedHashMap<>(extra);
        metadata.put("policyVersionId", version.id().toString());
        metadata.put("policyType", version.policyType().name());
        metadata.put("version", version.version());
        metadata.put("locale", version.locale());
        metadata.put("contentHash", version.contentHash());
        metadata.put("reason", adminAction.reason());
        auditLog.record(AuditEvent.builder()
                // actor_type = DPO: p11 §11.5.4 cho ĐÚNG vai trò này quyền publish chính sách,
                // nên ghi đúng nó thay vì gộp vào ADMIN (p4 §4.4.3 có cả hai giá trị).
                .actor(AuditActor.dpo(adminAction.actorId(), adminAction.actorRole()))
                .subject(AuditSubjectType.POLICY, null)
                .action(action)
                .metadata(metadata)
                .requestId(adminAction.requestId())
                .ipAddress(adminAction.ipAddress())
                .userAgent(adminAction.userAgent())
                .build());
    }

    /**
     * Ai làm, vì sao, từ đâu — p4 §4.6.3 + p15 REQ-AUD-03.
     *
     * @param actorId   người thực hiện
     * @param actorRole vai trò đã cho phép hành động đi qua (snapshot)
     * @param reason    lý do đã {@code strip()}, ≥ 10 ký tự (kiểm ở tầng api)
     * @param requestId {@code X-Request-Id} (p8 §8.1.9)
     * @param ipAddress IP nguồn
     * @param userAgent User-Agent nguồn
     */
    public record PolicyAdminAction(UUID actorId, String actorRole, String reason,
                                    String requestId, String ipAddress, String userAgent) {
    }
}
