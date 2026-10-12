package com.catcheck.scan.api;

import com.catcheck.scan.api.dto.DevManualScanRequest;
import com.catcheck.scan.application.ManualPipelineOutcomeFactory;
import com.catcheck.scan.application.ManualScanCommand;
import com.catcheck.scan.domain.CaptureSource;
import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.QualityFlagCode;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.ScanClassification;
import com.catcheck.shared.error.BusinessRuleException;

import java.util.List;
import java.util.UUID;

final class DevScanRequestSupport {

    private DevScanRequestSupport() {
    }

    static ManualScanCommand toCommand(UUID userId, DevManualScanRequest request, String idempotencyKey) {
        UUID catId = request.catId() == null || request.catId().isBlank()
                ? null : parseUuid(request.catId());
        ScanAssignment assignment = request.assignment() == null || request.assignment().isBlank()
                ? (catId == null ? ScanAssignment.SHARED_UNKNOWN : ScanAssignment.ASSIGNED)
                : parseEnum(ScanAssignment.class, request.assignment());
        CaptureSource captureSource = request.captureSource() == null || request.captureSource().isBlank()
                ? CaptureSource.CAMERA
                : parseEnum(CaptureSource.class, request.captureSource());
        ScanClassification forced = request.classification() == null || request.classification().isBlank()
                ? null
                : parseEnum(ScanClassification.class, request.classification());

        return new ManualScanCommand(
                userId, catId, assignment, request.capturedAt(), captureSource,
                request.deviceHint(), idempotencyKey, request.phValue(), forced,
                request.confidence(), toFlags(request.qualityFlags()));
    }

    private static List<QualityFlag> toFlags(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return codes.stream()
                .map(code -> parseEnum(QualityFlagCode.class, code))
                .map(ManualPipelineOutcomeFactory::flagOf)
                .toList();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
    }
}
