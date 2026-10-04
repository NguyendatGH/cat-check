package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một yêu cầu thực hiện quyền của chủ thể dữ liệu — dòng {@code dsar_request} (p4 B5,
 * p15 §15.4.2).
 *
 * <p><b>Mọi thao tác tự phục vụ cũng tạo một dsar_request</b> (p15 REQ-DSAR-01): bật/tắt
 * consent, xuất dữ liệu, xoá tài khoản — nếu không, sổ sách tuân thủ thiếu đúng phần lớn
 * nhất. {@code ack_due_at}/{@code fulfil_due_at} tính MỘT LẦN lúc tạo và không đổi khi
 * {@code holiday_calendar} cập nhật sau.</p>
 *
 * @param id                  UUID v7
 * @param publicRef           'DSAR-2026-000123' — mã user tra cứu, path param duy nhất (p8 §8.1.3)
 * @param userId              FK app_user(id) ON DELETE SET NULL — null khi người yêu cầu đã bị ẩn danh hoá
 * @param contactEmail        kênh trả lời — CHỐT LẠI lúc tạo; email D+8 đọc từ đây, không đọc app_user.email (p15 §15.4.6)
 * @param requestType         loại quyền (p15 §15.4.1)
 * @param channel             kênh nộp (SELF_SERVICE/WEB_FORM/EMAIL/POST)
 * @param status              RECEIVED/IDENTITY_PENDING/IN_PROGRESS/EXTENDED/COMPLETED/REJECTED
 * @param identityVerifiedAt  bắt buộc NOT NULL trước khi IN_PROGRESS với ACCESS_EXPORT/ERASE (I30, REQ-DSAR-04)
 * @param identityMethod       SESSION/EMAIL_OTP/ID_DOC_MANUAL
 * @param receivedAt          mốc bắt đầu đồng hồ SLA
 * @param ackDueAt            received_at + 2 ngày làm việc (qua holiday_calendar)
 * @param ackSentAt           mốc gửi email phản hồi
 * @param fulfilDueAt         10/15/20 ngày theo requestType (p15 §15.4.1)
 * @param extendedTo          gia hạn MỘT lần, tối đa bằng đúng thời hạn gốc
 * @param extensionReason     BẮT BUỘC khi extended_to IS NOT NULL (Đ5 NĐ356)
 * @param thirdPartyInvolved   kéo dài thời hạn theo bảng p15 §15.4.1
 * @param completedAt         mốc hoàn tất
 * @param rejectionReason     BẮT BUỘC khi REJECTED (Đ13.3/Đ14.5)
 * @param resultRef           storage key của gói ZIP (ACCESS_EXPORT); null sau khi hết 72 giờ
 * @param resultExpiresAt     link tải hết hạn sau 72 giờ
 * @param resultDownloadedAt  link MỘT LẦN — có giá trị ⇒ từ chối lần tải thứ hai
 * @param handledBy           DPO xử lý — FK app_user(id) ON DELETE SET NULL
 * @param pseudonymId         thay user_id sau khi tài khoản bị ẩn danh hoá; giữ thêm 5 năm
 * @param createdAt           mốc tạo
 */
public record DsarRequest(
        UUID id,
        String publicRef,
        UUID userId,
        String contactEmail,
        DsarRequestType requestType,
        DsarChannel channel,
        DsarStatus status,
        Instant identityVerifiedAt,
        String identityMethod,
        Instant receivedAt,
        Instant ackDueAt,
        Instant ackSentAt,
        Instant fulfilDueAt,
        Instant extendedTo,
        String extensionReason,
        boolean thirdPartyInvolved,
        Instant completedAt,
        String rejectionReason,
        String resultRef,
        Instant resultExpiresAt,
        Instant resultDownloadedAt,
        UUID handledBy,
        UUID pseudonymId,
        Instant createdAt
) {

    public DsarRequest {
        if (id == null || publicRef == null || contactEmail == null) {
            throw new IllegalArgumentException("dsarRequest thiếu trường bắt buộc");
        }
        if (requestType == null || channel == null || status == null) {
            throw new IllegalArgumentException("dsarRequest bắt buộc có requestType, channel, status");
        }
        if (receivedAt == null || ackDueAt == null || fulfilDueAt == null) {
            throw new IllegalArgumentException("dsarRequest bắt buộc tính ackDueAt và fulfilDueAt lúc tạo (p4 B5)");
        }
        if (!ackDueAt.isAfter(receivedAt) || !fulfilDueAt.isAfter(receivedAt)) {
            throw new IllegalArgumentException("dsarRequest ackDueAt/fulfilDueAt phải sau receivedAt (ck_dsar_request_*)");
        }
        if (extendedTo != null && extensionReason == null) {
            throw new IllegalArgumentException("dsarRequest gia hạn bắt buộc có extensionReason (ck_dsar_request_extension_reason)");
        }
        if (extendedTo != null && !extendedTo.isAfter(fulfilDueAt)) {
            throw new IllegalArgumentException("dsarRequest.extendedTo phải sau fulfilDueAt (ck_dsar_request_extended)");
        }
        if (status == DsarStatus.REJECTED && rejectionReason == null) {
            throw new IllegalArgumentException("dsarRequest REJECTED bắt buộc có rejectionReason (Đ13.3/Đ14.5)");
        }
    }

    /** Số ngày "thực hiện" theo p15 §15.4.1 — dùng tính fulfilDueAt lúc tạo yêu cầu. */
    public static int fulfilDaysFor(DsarRequestType type) {
        return switch (type) {
            case ACCESS_EXPORT, RECTIFY -> 10;
            case RESTRICT, OBJECT, WITHDRAW_CONSENT, PROTECTION_MEASURE, COMPLAINT -> 15;
            case ERASE -> 20;
        };
    }

    /** Yêu cầu xuất/xoá bắt buộc xác minh danh tính trước khi IN_PROGRESS (I30). */
    public boolean requiresIdentityVerification() {
        return requestType == DsarRequestType.ACCESS_EXPORT || requestType == DsarRequestType.ERASE;
    }

    /** Link tải gói xuất: chỉ tồn khi đã COMPLETED, chưa tải, chưa hết hạn. */
    public boolean isDownloadable(Instant now) {
        return status == DsarStatus.COMPLETED && resultRef != null && resultDownloadedAt == null
                && resultExpiresAt != null && resultExpiresAt.isAfter(now);
    }

    /** Bản sao đổi trạng thái — record immutable, mọi cập nhật đi qua constructor. */
    public DsarRequest withStatus(DsarStatus newStatus) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, newStatus,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, ackSentAt, fulfilDueAt,
                extendedTo, extensionReason, thirdPartyInvolved, completedAt, rejectionReason,
                resultRef, resultExpiresAt, resultDownloadedAt, handledBy, pseudonymId, createdAt);
    }

    /** Bản sao đánh dấu link tải đã dùng (một lần). */
    public DsarRequest withResultDownloadedAt(Instant downloadedAt) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, status,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, ackSentAt, fulfilDueAt,
                extendedTo, extensionReason, thirdPartyInvolved, completedAt, rejectionReason,
                resultRef, resultExpiresAt, downloadedAt, handledBy, pseudonymId, createdAt);
    }

    /** Bản sao ghi nhận đã gửi phản hồi tiếp nhận. */
    public DsarRequest withAckSentAt(Instant sentAt) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, status,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, sentAt, fulfilDueAt,
                extendedTo, extensionReason, thirdPartyInvolved, completedAt, rejectionReason,
                resultRef, resultExpiresAt, resultDownloadedAt, handledBy, pseudonymId, createdAt);
    }

    /** Bản sao gán người xử lý. */
    public DsarRequest withHandledBy(UUID handlerId) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, status,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, ackSentAt, fulfilDueAt,
                extendedTo, extensionReason, thirdPartyInvolved, completedAt, rejectionReason,
                resultRef, resultExpiresAt, resultDownloadedAt, handlerId, pseudonymId, createdAt);
    }

    /** Bản sao ghi nhận một lần gia hạn và lý do bắt buộc. */
    public DsarRequest withExtension(Instant newDueAt, String reason) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, status,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, ackSentAt, fulfilDueAt,
                newDueAt, reason, thirdPartyInvolved, completedAt, rejectionReason,
                resultRef, resultExpiresAt, resultDownloadedAt, handledBy, pseudonymId, createdAt);
    }

    /** Bản sao ghi lý do từ chối trước khi chuyển sang REJECTED. */
    public DsarRequest withRejectionReason(String reason) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, status,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, ackSentAt, fulfilDueAt,
                extendedTo, extensionReason, thirdPartyInvolved, completedAt, reason,
                resultRef, resultExpiresAt, resultDownloadedAt, handledBy, pseudonymId, createdAt);
    }

    /** Bản sao đánh dấu mốc hoàn tất. */
    public DsarRequest withCompletedAt(Instant completedAt) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, status,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, ackSentAt, fulfilDueAt,
                extendedTo, extensionReason, thirdPartyInvolved, completedAt, rejectionReason,
                resultRef, resultExpiresAt, resultDownloadedAt, handledBy, pseudonymId, createdAt);
    }

    /** Hoàn tất một bản xuất đã được DataExportJob ghi an toàn vào kho riêng. */
    public DsarRequest withExportResult(String storageRef, Instant expiresAt, Instant completedAt) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel,
                DsarStatus.COMPLETED, identityVerifiedAt, identityMethod, receivedAt, ackDueAt,
                ackSentAt, fulfilDueAt, extendedTo, extensionReason, thirdPartyInvolved,
                completedAt, rejectionReason, storageRef, expiresAt, null, handledBy,
                pseudonymId, createdAt);
    }
}
