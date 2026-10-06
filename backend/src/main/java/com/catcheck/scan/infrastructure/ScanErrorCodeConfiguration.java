package com.catcheck.scan.infrastructure;

import com.catcheck.scan.api.ScanErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký {@link ScanErrorCode} vào {@code shared.error.ErrorCodeRegistry} — mỗi hằng một
 * {@code @Bean ErrorCode} riêng, đúng khuôn {@code CreditErrorCodeConfiguration} /
 * {@code IdentityErrorCodeConfiguration} (KHÔNG dùng {@code @Bean List<ErrorCode>}, xem javadoc
 * của các lớp đó).
 *
 * <p>Đã kiểm tra trùng mã với {@code identity}/{@code privacy}/{@code cat}/{@code credit}/
 * {@code colorchart} (đọc {@code *ErrorCode.java} hiện có của từng module): 24 mã của scan đều
 * duy nhất. {@code CREDIT_INSUFFICIENT}/{@code WRITE_ACCESS_EXPIRED} KHÔNG lặp lại — scan ném
 * thẳng {@code credit.api.CreditErrorCode} (một mã lỗi một nguồn sự thật).</p>
 */
@Configuration
public class ScanErrorCodeConfiguration {

    @Bean
    ErrorCode scanMetadataInvalid() {
        return ScanErrorCode.SCAN_METADATA_INVALID;
    }

    @Bean
    ErrorCode imageTooSmall() {
        return ScanErrorCode.IMAGE_TOO_SMALL;
    }

    @Bean
    ErrorCode imageTooLarge() {
        return ScanErrorCode.IMAGE_TOO_LARGE;
    }

    @Bean
    ErrorCode imageFormatUnsupported() {
        return ScanErrorCode.IMAGE_FORMAT_UNSUPPORTED;
    }

    @Bean
    ErrorCode imageDecodeFailed() {
        return ScanErrorCode.IMAGE_DECODE_FAILED;
    }

    @Bean
    ErrorCode catNotOwned() {
        return ScanErrorCode.CAT_NOT_OWNED;
    }

    @Bean
    ErrorCode scanInProgress() {
        return ScanErrorCode.SCAN_IN_PROGRESS;
    }

    @Bean
    ErrorCode scanRequestNotFound() {
        return ScanErrorCode.SCAN_REQUEST_NOT_FOUND;
    }

    @Bean
    ErrorCode scanNotFound() {
        return ScanErrorCode.SCAN_NOT_FOUND;
    }

    @Bean
    ErrorCode scanAnalysisNotFound() {
        return ScanErrorCode.SCAN_ANALYSIS_NOT_FOUND;
    }

    @Bean
    ErrorCode scanImageNotStored() {
        return ScanErrorCode.SCAN_IMAGE_NOT_STORED;
    }

    @Bean
    ErrorCode scanImageExpired() {
        return ScanErrorCode.SCAN_IMAGE_EXPIRED;
    }

    @Bean
    ErrorCode scanImageDsarRequired() {
        return ScanErrorCode.SCAN_IMAGE_DSAR_REQUIRED;
    }

    @Bean
    ErrorCode scanAlreadyDeleted() {
        return ScanErrorCode.SCAN_ALREADY_DELETED;
    }

    @Bean
    ErrorCode scanReassignWindowClosed() {
        return ScanErrorCode.SCAN_REASSIGN_WINDOW_CLOSED;
    }

    @Bean
    ErrorCode scanReassignLimitReached() {
        return ScanErrorCode.SCAN_REASSIGN_LIMIT_REACHED;
    }

    @Bean
    ErrorCode scanNotReassignable() {
        return ScanErrorCode.SCAN_NOT_REASSIGNABLE;
    }

    @Bean
    ErrorCode scanReassignSameTarget() {
        return ScanErrorCode.SCAN_REASSIGN_SAME_TARGET;
    }

    @Bean
    ErrorCode scanAlreadyDisputed() {
        return ScanErrorCode.SCAN_ALREADY_DISPUTED;
    }

    @Bean
    ErrorCode scanDisputeNotFound() {
        return ScanErrorCode.SCAN_DISPUTE_NOT_FOUND;
    }

    @Bean
    ErrorCode scanChartUnavailable() {
        return ScanErrorCode.SCAN_CHART_UNAVAILABLE;
    }

    @Bean
    ErrorCode visionEngineUnavailable() {
        return ScanErrorCode.VISION_ENGINE_UNAVAILABLE;
    }

    @Bean
    ErrorCode scanPipelineError() {
        return ScanErrorCode.SCAN_PIPELINE_ERROR;
    }

    @Bean
    ErrorCode scanBusy() {
        return ScanErrorCode.SCAN_BUSY;
    }

    @Bean
    ErrorCode sortNotSupportedWithCursor() {
        return ScanErrorCode.SORT_NOT_SUPPORTED_WITH_CURSOR;
    }
}
