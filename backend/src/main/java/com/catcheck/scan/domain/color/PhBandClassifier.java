package com.catcheck.scan.domain.color;

import java.util.List;

public final class PhBandClassifier {

    private PhBandClassifier() {
        throw new AssertionError("PhBandClassifier la lop tien ich, khong instantiate");
    }

    public record Band(
            Code code,
            Double minPh,
            Double maxPh,
            boolean minInclusive,
            boolean maxInclusive,
            String labelKey,
            Severity severity,
            String colorToken,
            String icon,
            int sortOrder) {

        public boolean contains(double ph) {
            boolean aboveMin = minPh == null || (minInclusive ? ph >= minPh : ph > minPh);
            boolean belowMax = maxPh == null || (maxInclusive ? ph <= maxPh : ph < maxPh);
            return aboveMin && belowMax;
        }
    }

    public enum Code {
        IN_RANGE, SLIGHTLY_LOW, SLIGHTLY_HIGH, LOW, HIGH, INCONCLUSIVE
    }

    public enum Severity {
        NORMAL, ATTENTION, WATCH, NEUTRAL
    }

    public record Result(Band band, boolean nearBoundary) {

        public Code code() {
            return band.code();
        }
    }

    public static Result classify(double ph, double phLow, double phHigh, double confidence,
                                  List<Band> bands, boolean blocking) {
        if (bands == null || bands.isEmpty()) {
            throw new IllegalArgumentException("Bang phan loai rong — phai seed tu ph_classification_band");
        }
        if (confidence < ConfidenceCalculator.MIN_RESULT_CONFIDENCE || blocking) {
            return new Result(requireCode(bands, Code.INCONCLUSIVE), false);
        }
        Band matched = bands.stream()
                .filter(band -> band.contains(ph))
                .min((x, y) -> Integer.compare(x.sortOrder(), y.sortOrder()))
                .orElseThrow(() -> new IllegalStateException(
                        "Khong co dai nao chua pH " + ph + " — bang phan loai co lo hoac cho phep trong khoang"));
        return new Result(matched, nearBoundary(phLow, phHigh, bands));
    }

    public static boolean nearBoundary(double phLow, double phHigh, List<Band> bands) {
        Band inRange = requireCode(bands, Code.IN_RANGE);
        if (inRange.minPh() == null || inRange.maxPh() == null) {
            return false;
        }
        return straddles(phLow, phHigh, inRange.minPh()) || straddles(phLow, phHigh, inRange.maxPh());
    }

    private static boolean straddles(double phLow, double phHigh, double boundary) {
        return phLow < boundary && phHigh > boundary;
    }

    private static Band requireCode(List<Band> bands, Code code) {
        return bands.stream()
                .filter(band -> band.code() == code)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Bang phan loai thieu ma " + code));
    }
}
