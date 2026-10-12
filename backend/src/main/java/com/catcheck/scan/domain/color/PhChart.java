package com.catcheck.scan.domain.color;

import java.util.List;

public record PhChart(
        String id,
        int version,
        Status status,
        String illuminant,
        int observer,
        boolean isPlaceholder,
        List<PhChartPoint> points,
        DeltaE2000Params deltaEParams) {

    public PhChart {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("Bang mau phai co it nhat mot muc pH");
        }
        points = List.copyOf(points);
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).phValue() < points.get(i - 1).phValue()) {
                throw new IllegalArgumentException(
                        "Cac muc pH phai don dieu tang dan; muc thu " + (i + 1) + " sai thu tu");
            }
        }
        deltaEParams = deltaEParams == null ? DeltaE2000Params.CAT_CHECK : deltaEParams;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public double minPh() {
        return points.getFirst().phValue();
    }

    public double maxPh() {
        return points.getLast().phValue();
    }

    public static PhChart of(String id, List<PhChartPoint> points) {
        return new PhChart(id, 1, Status.ACTIVE, "D65", 2, true, points, DeltaE2000Params.CAT_CHECK);
    }

    public enum Status {
        DRAFT, ACTIVE, ARCHIVED
    }
}
