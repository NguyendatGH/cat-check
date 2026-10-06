package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.application.GlobalSessionPurgeService;

/**
 * Kết quả L62. {@code revokedCount} là con số mà p11 §11.13.4 đòi ghi vào audit
 * {@code SECURITY.SESSIONS_PURGED_ALL}; {@code elapsedMillis} để đối chiếu mục tiêu ≤ 5 phút.
 */
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
