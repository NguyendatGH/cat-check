package com.catcheck.scan.domain.color;

import java.util.List;

public final class GrainSelector {

    private GrainSelector() {
        throw new AssertionError("GrainSelector la lop tien ich, khong instantiate");
    }

    public record Candidate(int areaPx, Lab meanLab, double maxL, double minL) {
    }

    public record Selection(List<Lab> kept, double indicatorRatio, boolean offCurve) {

        public boolean isEmpty() {
            return kept.isEmpty();
        }
    }

    public static Selection select(List<Candidate> candidates, Lab substrateLab,
                                   VisionPortThresholds thresholds, int roiPixelCount,
                                   double nearestChartDeltaE) {
        if (candidates == null) {
            throw new IllegalArgumentException("Danh sach vung khong duoc null");
        }
        if (roiPixelCount <= 0) {
            throw new IllegalArgumentException("Vung quan tam phai co it nhat 1 pixel");
        }

        List<Lab> kept = new java.util.ArrayList<>(candidates.size());
        int keptPixels = 0;

        for (Candidate candidate : candidates) {
            if (candidate.areaPx() < thresholds.minBlobPx()) {
                continue;
            }
            if (candidate.maxL() > thresholds.specularLMax() || candidate.minL() < thresholds.shadowLMin()) {
                continue;
            }
            double chroma = Math.hypot(candidate.meanLab().a(), candidate.meanLab().b());
            if (chroma < thresholds.chromaMin()) {
                continue;
            }
            double fromSubstrate = DeltaE2000.deltaE(candidate.meanLab(), substrateLab,
                    DeltaE2000Params.CAT_CHECK);
            if (fromSubstrate < thresholds.deltaEFromSubstrateMin()) {
                continue;
            }
            kept.add(candidate.meanLab());
            keptPixels += candidate.areaPx();
        }

        double ratio = Math.min(1.0, (double) keptPixels / roiPixelCount);
        boolean offCurve = nearestChartDeltaE > thresholds.clusterMaxDeltaE();

        return new Selection(List.copyOf(kept), ratio, offCurve);
    }

    public record VisionPortThresholds(double minCoverage, double hardMinCoverage, int minBlobPx,
                                      double specularLMax, double shadowLMin, double chromaMin,
                                      double deltaEFromSubstrateMin, double clusterMaxDeltaE) {

        public static VisionPortThresholds standard() {
            return new VisionPortThresholds(0.015, 0.005, 12, 95.0, 15.0, 10.0, 8.0, 14.0);
        }
    }

    public static boolean isInconclusive(Selection selection, VisionPortThresholds thresholds) {
        return selection.indicatorRatio() < thresholds.hardMinCoverage() || selection.isEmpty();
    }

    public static boolean isLowCoverage(Selection selection, VisionPortThresholds thresholds) {
        return selection.indicatorRatio() < thresholds.minCoverage();
    }
}
