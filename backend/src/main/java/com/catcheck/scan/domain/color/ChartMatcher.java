package com.catcheck.scan.domain.color;

import java.util.List;
import java.util.Optional;

public final class ChartMatcher {

    public static final double MATCH_SCALE_DE = 15.0;

    private static final double OUT_OF_RANGE_TOLERANCE = 4.0;

    public static final double CARD_CHART_MISMATCH_PH = 0.3;

    private ChartMatcher() {
        throw new AssertionError("ChartMatcher la lop tien inch, khong instantiate");
    }

    public record Match(
            double phEstimate,
            double deltaEMin,
            String matchedPointId,
            double interpolatedT,
            PhChartPoint lowerPoint,
            PhChartPoint upperPoint,
            double matchPercent,
            boolean outOfChartRange) {
    }

    public static Match match(Lab sample, PhChart chart, List<Lab> onCardPatches) {
        if (sample == null) {
            throw new IllegalArgumentException("Can mau do de khop");
        }
        if (chart == null || chart.points().size() < 2) {
            throw new IllegalArgumentException("Bang mau can it nhat 2 muc de noi suy pH");
        }
        boolean relative = onCardPatches != null && onCardPatches.size() == chart.points().size();
        if (onCardPatches != null && !relative && !onCardPatches.isEmpty()) {
            throw new IllegalArgumentException("So o tren the khong khop so muc cua bang mau: "
                    + onCardPatches.size() + " vs " + chart.points().size());
        }
        return relative ? matchOnCard(sample, chart, onCardPatches) : matchOnDatabase(sample, chart);
    }

    private static Match matchOnDatabase(Lab sample, PhChart chart) {
        DeltaE2000Params params = chart.deltaEParams();
        List<PhChartPoint> points = chart.points();
        List<Lab> references = points.stream().map(PhChartPoint::lab).toList();
        return project(sample, chart, references, params, outOfRangeCheck(sample, points, references, params));
    }

    private static Match matchOnCard(Lab sample, PhChart chart, List<Lab> onCardPatches) {
        return project(sample, chart, onCardPatches, chart.deltaEParams(), false);
    }

    private static Match project(Lab sample, PhChart chart, List<Lab> references,
                                 DeltaE2000Params params, boolean outOfRange) {
        List<PhChartPoint> points = chart.points();
        int referencesCount = references.size();

        int bestLower = 0;
        double bestResidual = Double.MAX_VALUE;
        double bestT = 0.0;
        int nearestIndex = 0;
        double nearestDistance = Double.MAX_VALUE;

        for (int i = 0; i < referencesCount; i++) {
            double distance = DeltaE2000.deltaE(sample, references.get(i), params);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
            if (i >= referencesCount - 1) {
                continue;
            }

            double segmentDeltaE = DeltaE2000.deltaE(references.get(i), references.get(i + 1), params);
            double t = segmentDeltaE < 1e-9
                    ? 0.0
                    : clamp01(DeltaE2000.deltaE(sample, references.get(i), params) / segmentDeltaE);
            Lab projected = interpolateLab(references.get(i), references.get(i + 1), t);
            double residual = DeltaE2000.deltaE(sample, projected, params);

            if (residual < bestResidual) {
                bestResidual = residual;
                bestLower = i;
                bestT = t;
            }
        }

        PhChartPoint lower = points.get(bestLower);
        PhChartPoint upper = points.get(bestLower + 1);
        PhChartPoint nearest = points.get(nearestIndex);

        double ph = round1(lower.phValue() + bestT * (upper.phValue() - lower.phValue()));
        ph = Math.max(chart.minPh(), Math.min(chart.maxPh(), ph));

        return new Match(ph, nearestDistance, nearest.id(), round2(bestT), lower, upper,
                matchPercent(nearestDistance), outOfRange);
    }

    private static Lab interpolateLab(Lab from, Lab to, double t) {
        return new Lab(
                from.l() + t * (to.l() - from.l()),
                from.a() + t * (to.a() - from.a()),
                from.b() + t * (to.b() - from.b()));
    }

    private static boolean outOfRangeCheck(Lab sample, List<PhChartPoint> points,
                                           List<Lab> references, DeltaE2000Params params) {
        int nearest = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < references.size(); i++) {
            double distance = DeltaE2000.deltaE(sample, references.get(i), params);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
        }
        boolean clampedAtEnd = nearest == 0
                ? DeltaE2000.deltaE(sample, references.get(1), params) > nearestDistance
                : nearest == references.size() - 1
                        ? DeltaE2000.deltaE(sample, references.get(nearest - 1), params) > nearestDistance
                        : false;
        double tolerance = Math.max(OUT_OF_RANGE_TOLERANCE, points.get(nearest).toleranceDeltaE());
        return clampedAtEnd && nearestDistance > tolerance;
    }

    public static double matchPercent(double deltaE00) {
        double ratio = 1.0 - deltaE00 / MATCH_SCALE_DE;
        return Math.round(Math.max(0.0, Math.min(1.0, ratio)) * 100.0);
    }

    public static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static boolean mismatches(double relativePh, double databasePh) {
        return Math.abs(relativePh - databasePh) > CARD_CHART_MISMATCH_PH;
    }

    public static Optional<PhChartPoint> nearestPoint(Lab sample, PhChart chart) {
        return chart.points().stream()
                .min((x, y) -> Double.compare(
                        DeltaE2000.deltaE(sample, x.lab(), chart.deltaEParams()),
                        DeltaE2000.deltaE(sample, y.lab(), chart.deltaEParams())));
    }
}
