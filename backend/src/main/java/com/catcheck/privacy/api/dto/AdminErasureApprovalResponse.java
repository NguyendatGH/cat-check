package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.application.AdminErasureApprovalService;

import java.time.Instant;
import java.util.UUID;

/**
 * Kết quả L53 — xoá đã thực thi xong trong transaction của chính request này.
 *
 * @param publicRef            mã tra cứu DSAR
 * @param subjectId            chủ thể dữ liệu đã bị xoá/ẩn danh hoá
 * @param participantsInvoked  số module đã tham gia xoá; giảm bất thường = có module sót dữ liệu
 * @param completedAt          mốc ghi vào {@code dsar_request.completed_at}
 */
public record AdminErasureApprovalResponse(
        String publicRef, UUID subjectId, Integer participantsInvoked, Instant completedAt) {

    public static AdminErasureApprovalResponse from(AdminErasureApprovalService.Result result) {
        return new AdminErasureApprovalResponse(result.publicRef(), result.subjectId(),
                result.participantsInvoked(), result.completedAt());
    }
}
