package com.catcheck.privacy.api.dto;

import java.time.Instant;

/**
 * Chi tiết một yêu cầu DSAR — response C15 (p8 §8.4.3). Chỉ trả cho chủ sở hữu;
 * người khác nhận 404 (p8 §8.2.5).
 *
 * @param publicRef           mã tra cứu
 * @param requestType         loại quyền
 * @param channel             kênh nộp
 * @param status              trạng thái
 * @param contactEmail        kênh trả lời (chính email user — dữ liệu của chính họ)
 * @param identityVerifiedAt  mốc xác minh danh tính
 * @param identityMethod      SESSION/EMAIL_OTP/ID_DOC_MANUAL
 * @param receivedAt          mốc bắt đầu SLA
 * @param ackDueAt            hạn phản hồi
 * @param ackSentAt           mốc đã gửi phản hồi
 * @param fulfilDueAt         hạn thực hiện
 * @param extendedTo          hạn sau gia hạn (một lần)
 * @param extensionReason     lý do gia hạn (bắt buộc khi có extendedTo)
 * @param thirdPartyInvolved   có bên thứ ba
 * @param completedAt         mốc hoàn tất
 * @param rejectionReason     lý do từ chối (bắt buộc khi REJECTED)
 * @param resultExpiresAt     hạn tải 72 giờ
 * @param resultDownloadedAt  mốc tải (link một lần)
 */
public record DsarRequestDetailView(
        String publicRef,
        String requestType,
        String channel,
        String status,
        String contactEmail,
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
        Instant resultExpiresAt,
        Instant resultDownloadedAt
) {
}
