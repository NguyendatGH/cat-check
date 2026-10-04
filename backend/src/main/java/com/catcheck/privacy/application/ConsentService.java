package com.catcheck.privacy.application;

import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.ConsentMethod;
import com.catcheck.privacy.domain.ConsentPurpose;
import com.catcheck.privacy.domain.ConsentRecord;
import com.catcheck.privacy.domain.ConsentStatus;
import com.catcheck.privacy.domain.PolicyType;
import com.catcheck.privacy.domain.PolicyVersion;
import com.catcheck.privacy.domain.port.ConsentPurposePort;
import com.catcheck.privacy.domain.port.ConsentRecordPort;
import com.catcheck.privacy.domain.port.PolicyVersionPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ghi nhận / rút / tra cứu sự đồng ý theo TỪNG mục đích (p15 §15.3).
 *
 * <p>Nguyên tắc pháp lý bắt buộc (p15 §15.3.1): mỗi mục đích là một checkbox riêng (C2);
 * từ chối mục tuỳ chọn không chặn đăng ký (C3); im lặng KHÔNG phải là đồng ý (C4); rút =
 * INSERT dòng {@code WITHDRAWN}, không bao giờ UPDATE dòng cũ (C7, bất biến I16).</p>
 *
 * <p>Mã lỗi {@code CONSENT_PURPOSE_UNKNOWN} thuộc nhóm (c) của p8: hằng này nằm trong
 * enum này và được ném trực tiếp, nhưng KHÔNG đăng ký bean — hằng cùng tên đã được
 * {@code identity.api.IdentityErrorCode} đăng ký và {@code ErrorCodeRegistry} ném lỗi
 * khi hai enum đã đăng ký trả cùng {@code code()}. Xem javadoc {@link com.catcheck.privacy.api.PrivacyErrorCode}
 * và {@code docs/handovers/A2.md}.</p>
 */
@Service
public class ConsentService {

    /**
     * Mã purpose trong {@code consent_purpose} (p15 §15.3.2). Chuỗi chứ không hằng của
     * {@code notification.domain}: bảng {@code consent_purpose} thuộc {@code privacy}, nên giá
     * trị là của module này — và {@code notification.domain} không nằm trong named interface
     * {@code notification::api} nên không import được.
     */
    private static final String HEALTH_REMINDER_PUSH = "HEALTH_REMINDER_PUSH";

    private final ConsentPurposePort purposePort;
    private final ConsentRecordPort recordPort;
    private final PolicyVersionPort policyPort;
    private final NotificationGateway notificationGateway;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public ConsentService(
            ConsentPurposePort purposePort,
            ConsentRecordPort recordPort,
            PolicyVersionPort policyPort,
            NotificationGateway notificationGateway,
            UuidV7 uuidV7,
            Clock clock
    ) {
        this.purposePort = purposePort;
        this.recordPort = recordPort;
        this.policyPort = policyPort;
        this.notificationGateway = notificationGateway;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /** C1 — danh mục mục đích đã resolve theo locale (nhãn, mô tả, mandatory, sensitive). */
    public List<ConsentPurpose> listPurposes(String locale) {
        return purposePort.findAllActive();
    }

    /**
     * C2 — trạng thái hiện hành của MỌI mục đích {@code active}. Mục chưa từng được hỏi
     * trả với trạng thái {@code NONE} (chưa đồng ý) — "im lặng không phải là đồng ý".
     */
    public List<ConsentCurrentState> listCurrent(UUID userId) {
        List<ConsentCurrentState> states = new ArrayList<>();
        for (ConsentPurpose purpose : purposePort.findAllActive()) {
            ConsentStatus status = recordPort.findLatest(userId, purpose.code())
                    .map(ConsentRecord::status)
                    .orElse(ConsentStatus.NONE);
            states.add(new ConsentCurrentState(purpose.code(), status));
        }
        return states;
    }

    /**
     * C3 — cấp hoặc rút nhiều purpose trong MỘT transaction. Mỗi lần ghi đều là INSERT
     * dòng mới (append-only); dòng cũ không bao giờ bị sửa.
     *
     * @param grants   các mục user khai báo; {@code granted = true} cấp, {@code false} rút/từ chối
     * @param method   kênh ghi nhận (WEB_CHECKBOX lúc đăng ký, WEB_TOGGLE ở Trung tâm riêng tư…)
     * @param surface  màn hình xuất phát (register, scan_first_run, privacy_center…)
     * @param locale   ngôn ngữ của văn bản user thực sự đọc
     */
    @Transactional
    public List<ConsentCurrentState> recordConsents(
            UUID userId,
            List<ConsentGrant> grants,
            ConsentMethod method,
            String surface,
            String locale,
            RequestEvidence evidence
    ) {
        if (grants == null || grants.isEmpty()) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "consents");
        }
        Instant now = clock.instant();
        PolicyVersion policy = policyPort.findCurrent(PolicyType.PRIVACY, locale, now)
                .orElseThrow(() -> new BusinessRuleException(
                        PrivacyErrorCode.VALIDATION_FAILED, "policyVersion"));

        Map<String, ConsentCurrentState> result = new LinkedHashMap<>();
        for (ConsentGrant grant : grants) {
            ConsentPurpose purpose = purposePort.findByCode(grant.purposeCode())
                    .orElseThrow(() -> new BusinessRuleException(
                            PrivacyErrorCode.CONSENT_PURPOSE_UNKNOWN, grant.purposeCode()));
            ConsentRecord latest = recordPort.findLatest(userId, purpose.code()).orElse(null);
            if (grant.granted()) {
                if (latest == null || latest.status() != ConsentStatus.GRANTED) {
                    recordPort.append(buildRecord(userId, purpose, ConsentStatus.GRANTED,
                            latest, policy, method, surface, locale, evidence, now));
                }
            } else {
                if (purpose.isMandatory()) {
                    // p15 §15.3.5: SERVICE_CORE tương đương yêu cầu xoá tài khoản — không rút được.
                    throw new BusinessRuleException(
                            PrivacyErrorCode.CONSENT_MANDATORY_CANNOT_WITHDRAW,
                            purpose.code(), "DELETE_ACCOUNT");
                }
                if (latest == null) {
                    // Từ chối ngay lúc được hỏi (checkbox không được tick) — vẫn là bằng chứng.
                    recordPort.append(buildRecord(userId, purpose, ConsentStatus.DENIED,
                            null, policy, method, surface, locale, evidence, now));
                } else if (latest.status() == ConsentStatus.GRANTED) {
                    recordPort.append(buildRecord(userId, purpose, ConsentStatus.WITHDRAWN,
                            latest, policy, method, surface, locale, evidence, now));
                    applyWithdrawEffect(userId, purpose.code());
                }
                // DENIED/WITHDRAWN → tắt lại: idempotent, không ghi dòng mới.
            }
            result.put(purpose.code(), new ConsentCurrentState(purpose.code(),
                    recordPort.findLatest(userId, purpose.code())
                            .map(ConsentRecord::status)
                            .orElse(ConsentStatus.NONE)));
        }
        return new ArrayList<>(result.values());
    }

    /** Rút một mục đích — dùng trong C3 ({@code granted = false}) và các luồng hạn chế xử lý. */
    @Transactional
    public void withdrawPurpose(UUID userId, String purposeCode, String surface, String locale, RequestEvidence evidence) {
        ConsentPurpose purpose = purposePort.findByCode(purposeCode)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.CONSENT_PURPOSE_UNKNOWN, purposeCode));
        if (purpose.isMandatory()) {
            throw new BusinessRuleException(
                    PrivacyErrorCode.CONSENT_MANDATORY_CANNOT_WITHDRAW, purposeCode, "DELETE_ACCOUNT");
        }
        ConsentRecord latest = recordPort.findLatest(userId, purposeCode).orElse(null);
        if (latest != null && latest.status() == ConsentStatus.GRANTED) {
            PolicyVersion policy = policyPort.findCurrent(PolicyType.PRIVACY, locale, clock.instant())
                    .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "policyVersion"));
            recordPort.append(buildRecord(userId, purpose, ConsentStatus.WITHDRAWN, latest,
                    policy, ConsentMethod.WEB_TOGGLE, surface, locale, evidence, clock.instant()));
            applyWithdrawEffect(userId, purposeCode);
        }
    }

    /**
     * Hậu quả kỹ thuật của một lần rút consent — p15 §15.3.5 bảng "tác động khi rút".
     *
     * <p>Hiện mới có một mục: {@code HEALTH_REMINDER_PUSH} ⇒ thu hồi mọi
     * {@code push_subscription} NGAY (p12 §12.3.9), không chờ job dọn hằng tuần. Chạy trong
     * cùng transaction với dòng {@code WITHDRAWN} vừa ghi, nên không có khoảnh khắc nào mà
     * "đã ghi rút" nhưng thiết bị vẫn còn nhận được push.</p>
     *
     * <p>Các purpose còn lại chưa có tác động kỹ thuật tự động nào — xem handoff H15.90.</p>
     */
    private void applyWithdrawEffect(UUID userId, String purposeCode) {
        if (HEALTH_REMINDER_PUSH.equals(purposeCode)) {
            notificationGateway.revokePushOnConsentWithdrawal(userId);
        }
    }

    /** C4 — lịch sử consent của user, mới nhất trước — bằng chứng tuân thủ. */
    public List<ConsentRecord> history(UUID userId, int limit) {
        return recordPort.findHistoryByUser(userId, clock.instant(), Math.min(limit, 100));
    }

    private ConsentRecord buildRecord(
            UUID userId,
            ConsentPurpose purpose,
            ConsentStatus status,
            ConsentRecord latest,
            PolicyVersion policy,
            ConsentMethod method,
            String surface,
            String locale,
            RequestEvidence evidence,
            Instant now
    ) {
        // consent_text_hash = SHA-256 của ĐÚNG chuỗi text cạnh checkbox: nhãn + mô tả.
        String consentText = purpose.labelVy() + "\n" + purpose.descriptionVy();
        return new ConsentRecord(
                uuidV7.generate(),
                userId,
                purpose.code(),
                status,
                policy.id(),
                policy.contentHash(),
                ContentHashes.sha256Hex(consentText),
                method,
                surface,
                locale,
                latest != null ? latest.id() : null,
                now,
                evidence.ipAddress(),
                evidence.userAgent(),
                evidence.requestId(),
                null,
                now);
    }
}
