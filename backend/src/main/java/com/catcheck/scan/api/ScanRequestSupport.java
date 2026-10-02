package com.catcheck.scan.api;

import com.catcheck.scan.api.dto.RoiInput;
import com.catcheck.scan.api.dto.ScanListItemResponse;
import com.catcheck.scan.api.dto.ScanMetadataRequest;
import com.catcheck.scan.api.dto.ScanResultResponse;
import com.catcheck.scan.application.SubmitScanCommand;
import com.catcheck.scan.domain.CaptureSource;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.color.port.VisionEngine;
import com.catcheck.scan.domain.port.RawImagePort;
import com.catcheck.scan.domain.port.ScanQueryRepository.Row;
import com.catcheck.shared.error.BusinessRuleException;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Hàm chuyển đổi DTO ↔ kiểu domain cho {@link ScanController} — tách riêng khỏi class
 * {@code @RestController} để KHÔNG vi phạm R4 (ArchUnit quét mọi method, kể cả private, của một
 * lớp mang {@code @RestController}; lớp thường không mang annotation đó thì không bị soi).
 */
final class ScanRequestSupport {

    private ScanRequestSupport() {
    }

    static SubmitScanCommand toCommand(UUID userId, ScanMetadataRequest metadata, String idempotencyKey, byte[] bytes) {
        ScanAssignment assignment = parseAssignment(metadata.assignment());
        UUID catId = metadata.catId() == null || metadata.catId().isBlank() ? null : parseUuid(metadata.catId());
        CaptureSource captureSource = metadata.captureSource() == null
                ? CaptureSource.CAMERA : parseCaptureSource(metadata.captureSource());
        RawImagePort.RoiRect roi = toRoi(metadata.roi());
        VisionEngine.QuadHint quadHint = toQuadHint(metadata.cardQuadHint());

        return new SubmitScanCommand(userId, catId, assignment, metadata.capturedAt(), captureSource,
                metadata.deviceHint(), idempotencyKey, roi, quadHint, bytes);
    }

    static ScanResultResponse toResultResponse(Row row, String catName, String imageUrl) {
        return ScanResultResponse.from(row, catName, imageUrl);
    }

    /**
     * Ánh xạ danh sách — cố ý nhận {@link Function} thay vì để {@code ScanController} tự
     * {@code .stream().map(row -> ...)}: một lambda có tham số {@code Row} (domain) khai báo
     * ngay trong lớp {@code @RestController} biên dịch thành method tổng hợp (synthetic) MANG
     * chữ ký đó trên chính lớp controller, và ArchUnit R4 quét cả method private/synthetic.
     */
    static List<ScanListItemResponse> toListItems(List<Row> rows, Function<UUID, String> catNameResolver) {
        return rows.stream()
                .map(row -> ScanListItemResponse.from(row, catNameResolver.apply(row.catId())))
                .toList();
    }

    private static ScanAssignment parseAssignment(String value) {
        try {
            return ScanAssignment.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
    }

    private static CaptureSource parseCaptureSource(String value) {
        try {
            return CaptureSource.valueOf(value);
        } catch (IllegalArgumentException ex) {
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

    private static RawImagePort.RoiRect toRoi(RoiInput roi) {
        if (roi == null) {
            return RawImagePort.RoiRect.fullFrame();
        }
        try {
            return new RawImagePort.RoiRect(roi.x(), roi.y(), roi.w(), roi.h());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
    }

    private static VisionEngine.QuadHint toQuadHint(double[][] points) {
        if (points == null || points.length != 4) {
            return VisionEngine.QuadHint.empty();
        }
        try {
            return VisionEngine.QuadHint.of(points[0], points[1], points[2], points[3]);
        } catch (IllegalArgumentException ex) {
            return VisionEngine.QuadHint.empty();
        }
    }
}
