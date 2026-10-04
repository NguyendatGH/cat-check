package com.catcheck.privacy.application;

import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.BusinessDays;
import com.catcheck.privacy.domain.DsarChannel;
import com.catcheck.privacy.domain.DsarRequest;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.DsarStatus;
import com.catcheck.privacy.domain.HolidayCalendarEntry;
import com.catcheck.privacy.domain.HolidaySource;
import com.catcheck.privacy.domain.port.DsarRequestPort;
import com.catcheck.privacy.domain.port.HolidayCalendarPort;
import com.catcheck.privacy.spi.StepUpVerificationPort;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.privacy.spi.UserAccountSnapshot;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Điều phối yêu cầu quyền của chủ thể dữ liệu — DSAR (p15 §15.4, p8 nhóm C).
 *
 * <p>Mọi thao tác tự phục vụ đều tạo {@code dsar_request} (REQ-DSAR-01). SLA: phản hồi
 * 02 ngày làm việc (tính qua {@code holiday_calendar}), thực hiện 10/15/20 ngày theo
 * loại yêu cầu (p15 §15.4.1). Xuất/xoá bắt buộc step-up re-authentication
 * (REQ-DSAR-04) — fail-closed khi chưa có adapter.</p>
 */
@Service
public class DsarService {

    public static final String SCOPE_DATA_EXPORT = "DATA_EXPORT";
    public static final String SCOPE_ACCOUNT_ERASE = "ACCOUNT_ERASE";

    /** 7 ngày ân hạn xoá tài khoản (TD-05, p15 §15.4.6). */
    public static final long DELETION_GRACE_DAYS = 7;
    /** Giới hạn 1 yêu cầu xuất / 24 giờ (p15 §15.4.5). */
    public static final long EXPORT_RATE_LIMIT_HOURS = 24;

    private static final Set<DsarRequestType> SELF_SERVICE_MANUAL_TYPES = EnumSet.of(
            DsarRequestType.RECTIFY, DsarRequestType.OBJECT,
            DsarRequestType.PROTECTION_MEASURE, DsarRequestType.COMPLAINT);

    private final DsarRequestPort dsarPort;
    private final UserAccountPort userAccountPort;
    private final StepUpVerificationPort stepUpVerificationPort;
    private final HolidayCalendarPort holidayCalendarPort;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public DsarService(
            DsarRequestPort dsarPort,
            UserAccountPort userAccountPort,
            StepUpVerificationPort stepUpVerificationPort,
            HolidayCalendarPort holidayCalendarPort,
            UuidV7 uuidV7,
            Clock clock
    ) {
        this.dsarPort = dsarPort;
        this.userAccountPort = userAccountPort;
        this.stepUpVerificationPort = stepUpVerificationPort;
        this.holidayCalendarPort = holidayCalendarPort;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /**
     * C6 — tạo {@code dsar_request(ACCESS_EXPORT)}. Giới hạn 1/24 giờ
     * ({@code DSAR_EXPORT_RATE_LIMITED}); bắt buộc step-up
     * ({@code DSAR_IDENTITY_VERIFICATION_REQUIRED}).
     */
    @Transactional
    public DsarRequest createExportRequest(UUID userId, RequestEvidence evidence) {
        requireStepUp(userId, SCOPE_DATA_EXPORT);
        Instant now = clock.instant();
        Instant since = now.minusSeconds(EXPORT_RATE_LIMIT_HOURS * 3600);
        DsarRequest latest = dsarPort.findLatestExportRequest(userId, since)
                .orElse(null);
        if (latest != null) {
            long retryAfter = since.plusSeconds(EXPORT_RATE_LIMIT_HOURS * 3600).getEpochSecond() - now.getEpochSecond();
            throw new BusinessRuleException(
                    PrivacyErrorCode.DSAR_EXPORT_RATE_LIMITED,
                    Math.max(retryAfter, 1), latest.receivedAt());
        }
        return saveSelfServiceRequest(userId, DsarRequestType.ACCESS_EXPORT, evidence, now);
    }

    /**
     * C9 — yêu cầu xoá tài khoản: tạo {@code dsar_request(ERASE)} + chuyển tài khoản
     * sang {@code DELETION_REQUESTED} qua {@link UserAccountPort} (adapter của identity).
     * Ân hạn 7 ngày (TD-05).
     */
    @Transactional
    public DsarRequest requestAccountDeletion(UUID userId, RequestEvidence evidence) {
        requireStepUp(userId, SCOPE_ACCOUNT_ERASE);
        // Partial unique index uq_dsar_request_open_erase là lưới cuối; kiểm tra trước để
        // trả lỗi thân thiện kèm publicRef/scheduledAt (p8 DELETION_ALREADY_REQUESTED).
        if (dsarPort.existsOpenEraseRequest(userId)) {
            DsarRequest open = findOpenEraseRequest(userId);
            throw new BusinessRuleException(
                    PrivacyErrorCode.DELETION_ALREADY_REQUESTED,
                    open.publicRef(), open.receivedAt(), open.receivedAt());
        }
        Instant now = clock.instant();
        DsarRequest request = saveSelfServiceRequest(userId, DsarRequestType.ERASE, evidence, now);
        userAccountPort.requestAccountDeletion(userId, now.plusSeconds(DELETION_GRACE_DAYS * 86400));
        return request;
    }

    /**
     * C10 — huỷ yêu cầu xoá trong ân hạn (cũng gọi được từ link email). Quá 7 ngày ⇒
     * {@code DELETION_GRACE_EXPIRED}; không có yêu cầu ⇒ {@code DELETION_NOT_REQUESTED}.
     */
    @Transactional
    public void cancelAccountDeletion(UUID userId) {
        DsarRequest open = findOpenEraseRequest(userId);
        Instant now = clock.instant();
        Instant deadline = open.receivedAt().plusSeconds(DELETION_GRACE_DAYS * 86400);
        if (now.isAfter(deadline)) {
            throw new BusinessRuleException(PrivacyErrorCode.DELETION_GRACE_EXPIRED, deadline);
        }
        userAccountPort.cancelAccountDeletion(userId);
        dsarPort.update(open.withStatus(DsarStatus.COMPLETED).withCompletedAt(now));
    }

    /**
     * C11 — bật hạn chế xử lý: dữ liệu GIỮ NGUYÊN, hệ thống chỉ lưu trữ (p15 §15.4.7).
     * Tạo {@code dsar_request(RESTRICT)} + chuyển tài khoản sang {@code RESTRICTED}.
     */
    @Transactional
    public void enableRestriction(UUID userId, RequestEvidence evidence) {
        UserAccountSnapshot snapshot = userAccountPort.snapshot(userId);
        if (snapshot.isProcessingRestricted()) {
            throw new BusinessRuleException(PrivacyErrorCode.RESTRICTION_ALREADY_ACTIVE);
        }
        Instant now = clock.instant();
        saveSelfServiceRequest(userId, DsarRequestType.RESTRICT, evidence, now);
        userAccountPort.setProcessingRestricted(userId);
    }

    /** C12 — rút yêu cầu hạn chế ⇒ về ACTIVE. Idempotent khi không hạn chế (không có mã lỗi "chưa hạn chế" trong p8). */
    @Transactional
    public void disableRestriction(UUID userId) {
        UserAccountSnapshot snapshot = userAccountPort.snapshot(userId);
        if (snapshot.isProcessingRestricted()) {
            userAccountPort.liftProcessingRestriction(userId);
        }
        dsarPort.findByUser(userId, clock.instant(), null, 50).stream()
                .filter(r -> r.requestType() == DsarRequestType.RESTRICT
                        && r.status() != DsarStatus.COMPLETED
                        && r.status() != DsarStatus.REJECTED)
                .findFirst()
                .ifPresent(open -> dsarPort.update(
                        open.withStatus(DsarStatus.COMPLETED).withCompletedAt(clock.instant())));
    }

    /**
     * C13 — DSAR không tự phục vụ được: {@code RECTIFY}, {@code OBJECT},
     * {@code PROTECTION_MEASURE}, {@code COMPLAINT}. Loại ngoài danh sách ⇒
     * {@code DSAR_REQUEST_TYPE_UNSUPPORTED}.
     */
    @Transactional
    public DsarRequest createManualRequest(UUID userId, DsarRequestType type, DsarChannel channel, RequestEvidence evidence) {
        if (!SELF_SERVICE_MANUAL_TYPES.contains(type)) {
            throw new BusinessRuleException(
                    PrivacyErrorCode.DSAR_REQUEST_TYPE_UNSUPPORTED,
                    SELF_SERVICE_MANUAL_TYPES.stream().map(Enum::name).sorted().toArray());
        }
        requireStepUp(userId, "DSAR_" + type.name());
        return saveSelfServiceRequest(userId, type, channel, evidence, clock.instant());
    }

    /**
     * Tạo DSAR do DPO/Super tiếp nhận thay mặt người dùng (p8 L54). Không tự động đổi
     * trạng thái tài khoản: các yêu cầu ERASE chỉ là đề xuất cho tới khi DPO duyệt ở
     * workflow hai người riêng.
     */
    @Transactional
    public DsarRequest createAdminRequest(UUID userId, DsarRequestType type, DsarChannel channel) {
        if (type == null || channel == null || channel == DsarChannel.SELF_SERVICE) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "requestType/channel");
        }
        if (type == DsarRequestType.ERASE && dsarPort.existsOpenEraseRequest(userId)) {
            DsarRequest open = findOpenEraseRequest(userId);
            throw new BusinessRuleException(
                    PrivacyErrorCode.DELETION_ALREADY_REQUESTED,
                    open.publicRef(), open.receivedAt(), open.receivedAt());
        }
        return saveRequest(userId, type, channel, clock.instant(), null, null,
                (type == DsarRequestType.ACCESS_EXPORT || type == DsarRequestType.ERASE)
                        ? DsarStatus.IDENTITY_PENDING : DsarStatus.RECEIVED);
    }

    /** C14 — yêu cầu của tôi, mới nhất trước (cursor pagination theo p8 §8.1.4). Caller đã clamp limit. */
    public List<DsarRequest> listMine(UUID userId, Instant before, UUID beforeId, int limit) {
        return dsarPort.findByUser(userId, before, beforeId, limit);
    }

    /** C7/C15 — chi tiết theo {@code publicRef}; không thuộc user ⇒ 404 (p8 §8.2.5). */
    public DsarRequest getByPublicRef(UUID userId, String publicRef) {
        DsarRequest request = dsarPort.findByPublicRef(publicRef)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.DSAR_NOT_FOUND));
        if (request.userId() != null && !request.userId().equals(userId)) {
            throw new BusinessRuleException(PrivacyErrorCode.DSAR_NOT_FOUND);
        }
        return request;
    }

    /**
     * C8 — đánh dấu link tải đã dùng (một lần). Gọi bởi tầng tải file sau khi xác
     * minh login + hạn 72 giờ + chưa tải. Trả về request đã cập nhật.
     */
    @Transactional
    public DsarRequest markDownloaded(UUID userId, String publicRef) {
        DsarRequest request = getByPublicRef(userId, publicRef);
        Instant now = clock.instant();
        if (request.status() != DsarStatus.COMPLETED || request.resultRef() == null) {
            throw new BusinessRuleException(PrivacyErrorCode.DSAR_EXPORT_NOT_READY, request.status().name());
        }
        if (request.resultDownloadedAt() != null) {
            throw new BusinessRuleException(
                    PrivacyErrorCode.DSAR_EXPORT_ALREADY_DOWNLOADED, request.resultDownloadedAt());
        }
        if (request.resultExpiresAt() == null || !request.resultExpiresAt().isAfter(now)) {
            throw new BusinessRuleException(PrivacyErrorCode.DSAR_EXPORT_EXPIRED, request.resultExpiresAt());
        }
        DsarRequest updated = request.withResultDownloadedAt(now);
        dsarPort.update(updated);
        return updated;
    }

    /** Yêu cầu xoá đang mở của user — ném DELETION_NOT_REQUESTED nếu không có (C10). */
    private DsarRequest findOpenEraseRequest(UUID userId) {
        return dsarPort.findByUser(userId, clock.instant(), null, 50).stream()
                .filter(r -> r.requestType() == DsarRequestType.ERASE
                        && r.status() != DsarStatus.COMPLETED
                        && r.status() != DsarStatus.REJECTED)
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.DELETION_NOT_REQUESTED));
    }

    private void requireStepUp(UUID userId, String scope) {
        if (!stepUpVerificationPort.isVerified(userId, scope)) {
            // Fail-closed: không có bằng chứng step-up thì không xuất/xoá (REQ-DSAR-04).
            throw new BusinessRuleException(PrivacyErrorCode.DSAR_IDENTITY_VERIFICATION_REQUIRED, null, null);
        }
    }

    private DsarRequest saveSelfServiceRequest(UUID userId, DsarRequestType type, RequestEvidence evidence, Instant now) {
        return saveSelfServiceRequest(userId, type, DsarChannel.SELF_SERVICE, evidence, now);
    }

    private DsarRequest saveSelfServiceRequest(
            UUID userId, DsarRequestType type, DsarChannel channel, RequestEvidence evidence, Instant now
    ) {
        return saveRequest(userId, type, channel, now, now, "SESSION", DsarStatus.RECEIVED);
    }

    private DsarRequest saveRequest(
            UUID userId,
            DsarRequestType type,
            DsarChannel channel,
            Instant now,
            Instant identityVerifiedAt,
            String identityMethod,
            DsarStatus initialStatus
    ) {
        UserAccountSnapshot snapshot = userAccountPort.snapshot(userId);
        Set<LocalDate> officialHolidays = holidayCalendarPort.allHolidays().stream()
                .filter(h -> h.source() == HolidaySource.OFFICIAL)
                .map(HolidayCalendarEntry::holidayDate)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Instant ackDueAt = BusinessDays.addBusinessDays(now, 2, officialHolidays, now);
        Instant fulfilDueAt = now.plusSeconds(DsarRequest.fulfilDaysFor(type) * 86400L);
        DsarRequest request = new DsarRequest(
                uuidV7.generate(),
                dsarPort.nextPublicRef(),
                userId,
                snapshot.email(),
                type,
                channel,
                initialStatus,
                identityVerifiedAt,
                identityMethod,
                now,
                ackDueAt,
                null,
                fulfilDueAt,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now);
        return dsarPort.save(request);
    }
}
