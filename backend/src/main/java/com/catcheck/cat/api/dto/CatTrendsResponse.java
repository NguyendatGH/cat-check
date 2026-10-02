package com.catcheck.cat.api.dto;

import com.catcheck.cat.application.CatInsightService;
import com.catcheck.cat.application.spi.ScanInsightPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * D13 {@code GET /cats/{catId}/trends} — hop dong o p8 §8.9 dong 1186:
 * {@code {range, points: [...], stats: {...}, bands: [...], chartVersions: [...]}}.
 *
 * <p>Them {@code from}/{@code to} so voi hop dong goc: client can biet cua so thuc te da duoc
 * server giai ra tu {@code range} de ve truc thoi gian ma khong phai tu suy.</p>
 */
public record CatTrendsResponse(
        String range,
        Instant from,
        Instant to,
        List<Point> points,
        Stats stats,
        List<Band> bands,
        List<Integer> chartVersions) {

    /** @param phValue {@code null} khi lan quet do {@code INCONCLUSIVE} */
    public record Point(
            Instant capturedAt,
            BigDecimal phValue,
            String classification,
            BigDecimal confidence,
            boolean nearBoundary,
            boolean disputed) {
    }

    public record Stats(
            BigDecimal median,
            BigDecimal min,
            BigDecimal max,
            int count,
            long inRangeCount,
            long lowConfidenceCount) {
    }

    /** Dai phan loai pH tu {@code ph_classification_band} — nguon nguong duy nhat cho bieu do. */
    public record Band(
            String code,
            BigDecimal minPh,
            BigDecimal maxPh,
            String severity,
            String label,
            String colorToken,
            int sortOrder) {
    }

    public static CatTrendsResponse from(CatInsightService.CatTrendsView view) {
        return new CatTrendsResponse(
                view.range(),
                view.from(),
                view.to(),
                view.points().stream().map(CatTrendsResponse::toPoint).toList(),
                toStats(view.stats()),
                view.bands().stream().map(CatTrendsResponse::toBand).toList(),
                view.chartVersions());
    }

    private static Point toPoint(ScanInsightPort.TrendPoint p) {
        return new Point(p.capturedAt(), p.phValue(), p.classification(),
                p.confidence(), p.nearBoundary(), p.disputed());
    }

    private static Band toBand(ScanInsightPort.PhBandView b) {
        return new Band(b.code(), b.minPh(), b.maxPh(), b.severity(),
                b.label(), b.colorToken(), b.sortOrder());
    }

    private static Stats toStats(CatInsightService.TrendStats s) {
        return new Stats(s.median(), s.min(), s.max(), s.count(),
                s.inRangeCount(), s.lowConfidenceCount());
    }
}
