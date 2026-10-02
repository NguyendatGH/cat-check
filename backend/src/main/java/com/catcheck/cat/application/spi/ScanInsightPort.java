package com.catcheck.cat.application.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cong doc du lieu quet + canh bao suc khoe cua mot con meo, phuc vu D12/D13 (p8 §8.4.4).
 *
 * <p><b>Vi sao la SPI rieng cua cat + JDBC tho:</b> {@code cat/package-info.java} khai
 * {@code allowedDependencies = {shared, identity, credit, media::api, audit, privacy::spi}} —
 * KHONG co {@code scan} lan {@code insight}. Adapter hien thuc cong nay doc thang bang
 * {@code scan}, {@code scan_analysis}, {@code health_flag}, {@code ph_classification_band}
 * bang SQL va KHONG import type nao cua {@code com.catcheck.scan.*} /
 * {@code com.catcheck.insight.*}, nen khong sinh canh phu thuoc Java moi va
 * {@code ModularityTests} khong bi anh huong. Day dung ky thuat ma
 * {@code JdbcUserAccountPortAdapter} (cat doc {@code app_user}) va
 * {@code scan/package-info.java} (scan doc bang {@code cat}) da dung va ghi chu cong khai.</p>
 *
 * <p>Stub 501 cu cua D12/D13 ghi ly do "cho {@code ScanSummaryPort}/{@code HealthFlagPort} tu
 * module scan/insight, hien chua ton tai" — ly do do da cu, hai module deu da co; nhung huong
 * phu thuoc {@code cat -> scan} van khong hop le, nen dung cong nay thay vi goi sang do.</p>
 */
public interface ScanInsightPort {

    /** Lan quet gan nhat da phan tich xong ({@code scan.status = 'ANALYZED'}), neu co. */
    Optional<LastScan> lastScanOf(UUID catId);

    /** Thong ke cua so thoi gian, dung cho {@code scanCount30d} / {@code inRangeRatio30d} (D12). */
    WindowStats windowStats(UUID catId, Instant from, Instant to);

    /** So canh bao suc khoe chua doc ({@code health_flag.acknowledged_at IS NULL}). */
    long unacknowledgedFlagCount(UUID catId);

    /** Chuoi diem pH trong cua so, sap xep tang dan theo {@code captured_at} (D13). */
    List<TrendPoint> trendPoints(UUID catId, Instant from, Instant to);

    /** Cac dai phan loai pH dang bat — nguon duy nhat cho nguong, KHONG hard-code trong Java. */
    List<PhBandView> activeBands();

    /** Cac phien ban bang mau da tham gia tao ra chuoi diem tren (D13 {@code chartVersions}). */
    List<Integer> chartVersionsIn(UUID catId, Instant from, Instant to);

    /**
     * @param phValue {@code null} khi lan quet do khong ket luan duoc ({@code INCONCLUSIVE})
     */
    record LastScan(
            UUID scanId,
            Instant capturedAt,
            BigDecimal phValue,
            String classification,
            BigDecimal confidence) {
    }

    /** @param inRangeCount so lan phan loai {@code IN_RANGE} trong cua so */
    record WindowStats(long scanCount, long inRangeCount, long conclusiveCount) {
    }

    record TrendPoint(
            Instant capturedAt,
            BigDecimal phValue,
            String classification,
            BigDecimal confidence,
            boolean nearBoundary,
            boolean disputed) {
    }

    /** Anh xa 1-1 cot cua {@code ph_classification_band} dang {@code active}. */
    record PhBandView(
            String code,
            BigDecimal minPh,
            BigDecimal maxPh,
            String severity,
            String label,
            String colorToken,
            int sortOrder) {
    }
}
