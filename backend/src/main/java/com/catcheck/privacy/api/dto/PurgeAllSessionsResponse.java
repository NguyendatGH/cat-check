package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.application.GlobalSessionPurgeService;

public record PurgeAllSessionsResponse(
        String incidentRef,
        Integer revokedCount,
        Integer deviceSessionsRevoked,
        Integer otpInvalidated,
        Integer dsarDownloadLinksRevoked,
        Long elapsedMillis) {

    public static PurgeAllSessionsResponse from(GlobalSessionPurgeService.Result result) {
        return new PurgeAllSessionsResponse(result.incidentRef(), result.revokedCount(),
                result.deviceSessionsRevoked(), result.otpInvalidated(),
                result.dsarDownloadLinksRevoked(), result.elapsedMillis());
    }
}
