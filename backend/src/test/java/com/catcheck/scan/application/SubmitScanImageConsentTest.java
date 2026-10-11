package com.catcheck.scan.application;

import com.catcheck.audit.api.AuditLogService;
import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.credit.api.EntitlementQuery;
import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.StorageKey;
import com.catcheck.media.api.StoredImage;
import com.catcheck.scan.domain.CaptureSource;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.ScanClassification;
import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.port.ScanAnalysisRepository;
import com.catcheck.scan.domain.port.ScanImageRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.shared.id.UuidV7;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gói có {@code storeImage} nhưng người dùng chưa/không đồng ý {@code SCAN_IMAGE_RETAIN}
 * ⇒ KHÔNG ghi ảnh ra storage, {@code store_image = false}, lý do {@code CONSENT_OFF}.
 *
 * <p>Lỗi cũ: chỉ xét gói + trial + inconclusive; consent tuỳ chọn (dữ liệu nhạy cảm, mặc định tắt)
 * bị bỏ qua hoàn toàn, {@code StoreImageReason.CONSENT_OFF} không bao giờ được dùng.</p>
 */
class SubmitScanImageConsentTest {

    private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");

    private final ImageStorage imageStorage = mock(ImageStorage.class);
    private final ScanImageRepository scanImageRepository = mock(ScanImageRepository.class);

    @Test
    void planAllowsButConsentMissingDoesNotStoreImage() {
        ScanSubmitResult result = service(false).submit(command());

        verify(imageStorage, never()).put(anyString(), any());
        verify(scanImageRepository, never()).save(any());
        assertThat(result.imageStored()).isFalse();
        assertThat(result.storeImageReason()).isEqualTo("CONSENT_OFF");
    }

    @Test
    void planAllowsAndConsentGrantedStoresImage() {
        ScanSubmitResult result = service(true).submit(command());

        verify(imageStorage).put(anyString(), any());
        assertThat(result.imageStored()).isTrue();
        assertThat(result.storeImageReason()).isNull();
    }

    private SubmitScanService service(boolean consentGranted) {
        ScanRepository scans = mock(ScanRepository.class);
        when(scans.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(scans.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ScanAnalysisRepository analyses = mock(ScanAnalysisRepository.class);
        when(analyses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CreditConsumption credit = mock(CreditConsumption.class);
        when(credit.hasTrialScanRemaining(USER_ID)).thenReturn(false);
        when(credit.hasAvailableCredit(USER_ID)).thenReturn(true);
        when(credit.consume(any(), any())).thenReturn(new CreditConsumption.CreditCharge(1, 4,
                List.of(new CreditConsumption.ChargedBatch(UUID.randomUUID(), UUID.randomUUID(), 1))));
        EntitlementQuery entitlements = mock(EntitlementQuery.class);
        when(entitlements.isFeatureEnabled(USER_ID, EntitlementQuery.Feature.STORE_IMAGE)).thenReturn(true);

        ChartCatalog noChart = mock(ChartCatalog.class);
        when(noChart.findActiveChart(any(), any())).thenReturn(Optional.empty());
        // Kết quả nhập tay không mang MIME; pipeline thật sniff ra image/jpeg.
        PipelineOutcome analyzed = spy(new ManualPipelineOutcomeFactory(noChart)
                .fromManualPh(null, ScanClassification.IN_RANGE, null, null));
        doReturn("image/jpeg").when(analyzed).imageContentType();
        ColorPipelineService pipeline = mock(ColorPipelineService.class);
        when(pipeline.analyze(any(), any(), any())).thenReturn(analyzed);

        when(imageStorage.put(anyString(), any())).thenReturn(new StoredImage(
                StorageKey.parse("scan-image/2026/10/11/a.jpg"), "LOCAL", 3, "image/jpeg"));

        ScanPersistenceService persistence = new ScanPersistenceService(
                scans, analyses, scanImageRepository, credit, mock(AuditLogService.class),
                mock(ApplicationEventPublisher.class), userId -> { }, new UuidV7(CLOCK), CLOCK);
        return new SubmitScanService(scans, analyses, pipeline,
                new ScanPipelineMetrics(new SimpleMeterRegistry()), persistence, null, credit,
                entitlements, userId -> consentGranted, imageStorage, CLOCK);
    }

    private static SubmitScanCommand command() {
        return new SubmitScanCommand(USER_ID, null, ScanAssignment.SHARED_UNKNOWN, NOW,
                CaptureSource.CAMERA, null, "idem-consent-" + UUID.randomUUID(), null, null,
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }
}
