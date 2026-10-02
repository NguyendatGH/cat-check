package com.catcheck.privacy.api;

import com.catcheck.privacy.api.dto.DsarRequestDetailView;
import com.catcheck.privacy.api.dto.DsarRequestView;
import com.catcheck.privacy.api.dto.ExportStatusView;
import com.catcheck.privacy.domain.DsarRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Chuyển {@link DsarRequest} (domain) thành DTO trả về client — tách riỏi controller
 * vì ArchUnit R4 quét MỌI method của {@code @RestController} (kể cả private) nên
 * controller không được chạm vào type {@code ..domain..}.
 */
final class PrivacyDtoMapper {

    private PrivacyDtoMapper() {
    }

    static ExportStatusView toExportStatus(DsarRequest request) {
        return new ExportStatusView(
                request.publicRef(),
                request.status().name(),
                request.ackDueAt(),
                request.fulfilDueAt(),
                request.resultExpiresAt(),
                request.completedAt());
    }

    static DsarRequestView toRequestView(DsarRequest request) {
        return new DsarRequestView(
                request.publicRef(),
                request.requestType().name(),
                request.channel().name(),
                request.status().name(),
                request.receivedAt(),
                request.ackDueAt(),
                request.fulfilDueAt());
    }

    static DsarRequestDetailView toDetailView(DsarRequest request) {
        return new DsarRequestDetailView(
                request.publicRef(),
                request.requestType().name(),
                request.channel().name(),
                request.status().name(),
                request.contactEmail(),
                request.identityVerifiedAt(),
                request.identityMethod(),
                request.receivedAt(),
                request.ackDueAt(),
                request.ackSentAt(),
                request.fulfilDueAt(),
                request.extendedTo(),
                request.extensionReason(),
                request.thirdPartyInvolved(),
                request.completedAt(),
                request.rejectionReason(),
                request.resultExpiresAt(),
                request.resultDownloadedAt());
    }

    /** Cursor base64url của {@code received_at|id} — client không được tự sinh (p8 §8.1.4). */
    static String encodeCursor(DsarRequest last) {
        String raw = last.receivedAt().toString() + "|" + last.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static String[] decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return raw.split("\\|", 2);
        } catch (IllegalArgumentException ex) {
            throw new com.catcheck.shared.error.BusinessRuleException(
                    PrivacyErrorCode.PAGINATION_CURSOR_INVALID);
        }
    }
}
