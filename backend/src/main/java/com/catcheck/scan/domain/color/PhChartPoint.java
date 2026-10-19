package com.catcheck.scan.domain.color;

public record PhChartPoint(
        String id,
        double phValue,
        Lab lab,
        double toleranceDeltaE,
        String hexSrgb,
        String displayHex,
        String displayNameVi,
        String displayNameEn,
        int sampleCount,
        double spreadDeltaE) {

    public static final double DEFAULT_TOLERANCE = 4.0;

    public double deltaETo(PhChartPoint other, DeltaE2000Params params) {
        return DeltaE2000.deltaE(lab, other.lab, params);
    }

    public String swatchHex() {
        return displayHex != null ? displayHex : ColorSpace.labToHex(lab);
    }
}
