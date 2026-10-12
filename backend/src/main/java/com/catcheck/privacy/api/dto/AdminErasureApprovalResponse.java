package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.application.AdminErasureApprovalService;

import java.time.Instant;
import java.util.UUID;

public record AdminErasureApprovalResponse(
        String publicRef, UUID subjectId, Integer participantsInvoked, Instant completedAt) {

    public static AdminErasureApprovalResponse from(AdminErasureApprovalService.Result result) {
        return new AdminErasureApprovalResponse(result.publicRef(), result.subjectId(),
                result.participantsInvoked(), result.completedAt());
    }
}
