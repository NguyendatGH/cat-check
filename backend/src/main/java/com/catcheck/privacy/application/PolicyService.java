package com.catcheck.privacy.application;

import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.PolicyAcknowledgment;
import com.catcheck.privacy.domain.PolicySurface;
import com.catcheck.privacy.domain.PolicyType;
import com.catcheck.privacy.domain.PolicyVersion;
import com.catcheck.privacy.domain.port.ConsentRecordPort;
import com.catcheck.privacy.domain.port.PolicyAcknowledgmentPort;
import com.catcheck.privacy.domain.port.PolicyVersionPort;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.privacy.spi.UserAccountSnapshot;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Quản lý phiên bản văn bản chính sách + ghi nhận "đã đọc" (p15 §15.9.1, §15.8).
 *
 * <p>Versioning semver (REQ-VER-01): {@code MAJOR} bump ⇒ bắt buộc
 * {@code requires_reconsent = true} + điền {@code affected_purposes}. Không bao giờ sửa
 * một version đã có consent trỏ tới — sửa nội dung = publish version mới (REQ-VER-07).
 * Chỉ role {@code DPO} được publish (p11 §11.5.4).</p>
 */
@Service
public class PolicyService {

    /** Cửa sổ 7 ngày báo trước qua email trước effective_from của bản MAJOR (REQ-VER-06). */
    public static final String ROLE_DPO = "DPO";

    /**
     * Ngôn ngữ mặc định — cũng là bản CÓ HIỆU LỰC PHÁP LÝ khi thiếu bản dịch
     * (p15 REQ-LEGAL-07, quyết định #15).
     */
    public static final String DEFAULT_LOCALE = "vi";

    /** Bốn loại tài liệu công khai ở Phase 1 (p8 C16). */
    private static final List<PolicyType> PUBLIC_TYPES = List.of(
            PolicyType.TERMS, PolicyType.PRIVACY, PolicyType.COOKIE, PolicyType.MEDICAL_DISCLAIMER);

    private final PolicyVersionPort policyPort;
    private final PolicyAcknowledgmentPort acknowledgmentPort;
    private final ConsentRecordPort recordPort;
    private final UserAccountPort userAccountPort;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public PolicyService(
            PolicyVersionPort policyPort,
            PolicyAcknowledgmentPort acknowledgmentPort,
            ConsentRecordPort recordPort,
            UserAccountPort userAccountPort,
            UuidV7 uuidV7,
            Clock clock
    ) {
        this.policyPort = policyPort;
        this.acknowledgmentPort = acknowledgmentPort;
        this.recordPort = recordPort;
        this.userAccountPort = userAccountPort;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /**
     * Publish một phiên bản mới. {@code contentHash} = SHA-256 của {@code contentMd}
     * (hoặc nội dung tại {@code contentUrl} lúc publish) — bằng chứng bất biến Điều 6.2 NĐ356.
     *
     * @param actor       user thực hiện — bắt buộc role DPO (p11 §11.5.4)
     * @param contentHash SHA-256 hex (64 ký tự) của nội dung lúc publish
     */
    @Transactional
    public PolicyVersion publish(
            UUID actor,
            PolicyType type,
            String version,
            String locale,
            String title,
            String contentMd,
            String contentUrl,
            String contentHash,
            String summaryOfChanges,
            boolean requiresReconsent,
            List<String> affectedPurposes,
            Instant effectiveFrom
    ) {
        UserAccountSnapshot snapshot = userAccountPort.snapshot(actor);
        if (!snapshot.hasRole(ROLE_DPO)) {
            throw new PermissionDeniedException(PrivacyErrorCode.FORBIDDEN, ROLE_DPO);
        }
        if (requiresReconsent && (affectedPurposes == null || affectedPurposes.isEmpty())) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "affectedPurposes");
        }
        PolicyVersion policy = new PolicyVersion(
                uuidV7.generate(),
                type,
                version,
                locale,
                title,
                contentMd,
                contentUrl,
                contentHash,
                summaryOfChanges,
                requiresReconsent,
                affectedPurposes,
                effectiveFrom,
                null,
                actor,
                clock.instant());
        policyPort.publish(policy);
        return policy;
    }

    /** C16 — bản đang hiệu lực của một loại tài liệu (công khai). */
    public Optional<PolicyVersion> current(PolicyType type, String locale) {
        return policyPort.findCurrent(type, locale, clock.instant());
    }

    /**
     * F9 — mọi phiên bản của một tài liệu, mới nhất trước (p15 REQ-LEGAL-02).
     *
     * <p>Trả cả bản đã hết hiệu lực: mục đích của màn "Xem các phiên bản trước" chính là
     * chứng minh user đã đồng ý với nội dung nào tại thời điểm nào.</p>
     */
    public List<PolicyVersion> allVersions(PolicyType type, String locale) {
        List<PolicyVersion> versions = policyPort.findAllByType(type, locale);
        if (versions.isEmpty() && !DEFAULT_LOCALE.equals(locale)) {
            // REQ-LEGAL-07: chưa có bản dịch thì lùi về tiếng Việt (bản có hiệu lực pháp lý).
            return policyPort.findAllByType(type, DEFAULT_LOCALE);
        }
        return versions;
    }

    /**
     * F10 — permalink một phiên bản cụ thể (p15 REQ-LEGAL-03: "phải truy cập được vĩnh viễn").
     *
     * <p>Không lọc theo thời gian hiệu lực — bản cũ vẫn phải mở được. Thiếu bản dịch thì lùi
     * về {@code vi} (REQ-LEGAL-07) thay vì 404, để URL đã phát ra ngoài không bao giờ chết.</p>
     */
    public Optional<PolicyVersion> versionPermalink(PolicyType type, String version, String locale) {
        Optional<PolicyVersion> found = policyPort.findByTypeAndVersion(type, version, locale);
        if (found.isEmpty() && !DEFAULT_LOCALE.equals(locale)) {
            return policyPort.findByTypeAndVersion(type, version, DEFAULT_LOCALE);
        }
        return found;
    }

    /**
     * Parse mã loại tài liệu từ path — chỉ 4 loại công khai ở Phase 1 (p8 C16). Nằm ở
     * service (không phải controller) vì ArchUnit R4 cấm method của controller trả về
     * type {@code ..domain..}.
     */
    public PolicyType parsePublicType(String policyCode) {
        try {
            PolicyType type = PolicyType.valueOf(policyCode);
            if (!PUBLIC_TYPES.contains(type)) {
                throw new BusinessRuleException(PrivacyErrorCode.POLICY_VERSION_NOT_FOUND, policyCode);
            }
            return type;
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(PrivacyErrorCode.POLICY_VERSION_NOT_FOUND, policyCode);
        }
    }

    /**
     * Kiểm tra user đã xác nhận bản disclaimer hiện hành chưa — gọi ở mỗi lần vào
     * màn kết quả / onboarding (p4 B4).
     */
    public boolean hasAcknowledged(UUID userId, PolicyType type, PolicySurface surface) {
        Optional<PolicyVersion> current = policyPort.findCurrent(type, "vi", clock.instant());
        if (current.isEmpty()) {
            return true;
        }
        return acknowledgmentPort.exists(userId, current.get().id(), surface);
    }

    /**
     * C17 — ghi {@code policy_acknowledgment} (disclaimer onboarding — KHÔNG phải
     * consent). Idempotent: UNIQUE (user_id, policy_version_id, surface).
     *
     * @param locale ngôn ngữ của bản văn bản user đang xem ('vi'/'en')
     */
    @Transactional
    public void acknowledge(UUID userId, PolicyType type, String locale, PolicySurface surface, RequestEvidence evidence) {
        PolicyVersion policy = policyPort.findCurrent(type, locale, clock.instant())
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.POLICY_VERSION_NOT_FOUND, type.name()));
        if (acknowledgmentPort.exists(userId, policy.id(), surface)) {
            return;
        }
        acknowledgmentPort.append(new PolicyAcknowledgment(
                uuidV7.generate(),
                userId,
                policy.id(),
                policy.contentHash(),
                surface,
                clock.instant(),
                evidence.ipAddress(),
                evidence.userAgent(),
                clock.instant()));
    }

    /**
     * Cồng consent gate (p15 REQ-VER-03): các mục đích cần user xác nhận lại vì có
     * bản {@code requires_reconsent = true} mới hơn bản consent hiện hành của user.
     * Trả về danh sách mã purpose; rỗng = thông suốt.
     */
    public List<String> pendingReconsentPurposes(UUID userId) {
        Instant now = clock.instant();
        List<PolicyVersion> latestByType = new ArrayList<>();
        for (PolicyType type : List.of(PolicyType.PRIVACY, PolicyType.TERMS)) {
            policyPort.findCurrent(type, "vi", now).ifPresent(latestByType::add);
        }
        List<String> pending = new ArrayList<>();
        for (PolicyVersion policy : latestByType) {
            if (!policy.requiresReconsent()) {
                continue;
            }
            for (String purposeCode : policy.affectedPurposes()) {
                boolean userConsentedUnderThisPolicy = recordPort.findLatest(userId, purposeCode)
                        .map(record -> record.policyVersionId().equals(policy.id()))
                        .orElse(false);
                if (!userConsentedUnderThisPolicy && !pending.contains(purposeCode)) {
                    pending.add(purposeCode);
                }
            }
        }
        return pending;
    }
}
