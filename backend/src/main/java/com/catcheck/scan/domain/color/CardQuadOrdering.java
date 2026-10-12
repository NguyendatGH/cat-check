package com.catcheck.scan.domain.color;

import com.catcheck.scan.domain.color.port.VisionEngine.QuadHint;

import java.util.Comparator;
import java.util.List;

public final class CardQuadOrdering {

    private CardQuadOrdering() {
        throw new AssertionError("CardQuadOrdering la lop tien ich, khong instantiate");
    }

    public static QuadHint canonicalize(List<double[]> points) {
        if (points == null || points.size() != QuadHint.POINT_COUNT) {
            throw new IllegalArgumentException("Can dung 4 dinh tham chieu, nhan duoc "
                    + (points == null ? 0 : points.size()));
        }
        for (double[] point : points) {
            if (point == null || point.length != 2) {
                throw new IllegalArgumentException("Moi dinh phai la cap [x, y]");
            }
        }

        List<double[]> ring = sortAroundCenter(points);

        int start = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < ring.size(); i++) {
            double sum = ring.get(i)[0] + ring.get(i)[1];
            if (sum < best) {
                best = sum;
                start = i;
            }
        }

        double[] topLeft = ring.get(start);
        double[] topRight = ring.get((start + 1) % QuadHint.POINT_COUNT);
        double[] bottomRight = ring.get((start + 2) % QuadHint.POINT_COUNT);
        double[] bottomLeft = ring.get((start + 3) % QuadHint.POINT_COUNT);

        QuadHint quad = QuadHint.of(copy(topLeft), copy(topRight), copy(bottomRight), copy(bottomLeft));
        if (Math.abs(signedArea(quad)) < 1e-9) {
            throw new IllegalArgumentException("Tu giac bi tho eo bang 0 — khong nap duoc anh the");
        }
        return quad;
    }

    public static double signedArea(QuadHint quad) {
        double sum = 0.0;
        double[][] corners = {quad.topLeft(), quad.topRight(), quad.bottomRight(), quad.bottomLeft()};
        for (int i = 0; i < corners.length; i++) {
            double[] current = corners[i];
            double[] next = corners[(i + 1) % corners.length];
            sum += current[0] * next[1] - next[0] * current[1];
        }
        return sum / 2.0;
    }

    public static boolean isPlausible(QuadHint quad, int minAreaPx) {
        double area = Math.abs(signedArea(quad));
        if (area < minAreaPx) {
            return false;
        }
        double width = distance(quad.topLeft(), quad.topRight());
        double height = distance(quad.topLeft(), quad.bottomLeft());
        if (width <= 0 || height <= 0) {
            return false;
        }
        double ratio = width / height;
        return ratio > 0.4 && ratio < 2.5;
    }

    public static final int MIN_CARD_AREA_PX = 20_000;

    private static double[] copy(double[] point) {
        return new double[] {point[0], point[1]};
    }

    private static double distance(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    public static List<double[]> sortAroundCenter(List<double[]> points) {
        double centerX = points.stream().mapToDouble(p -> p[0]).average().orElseThrow();
        double centerY = points.stream().mapToDouble(p -> p[1]).average().orElseThrow();
        return points.stream()
                .sorted(Comparator.comparingDouble(p -> Math.atan2(p[1] - centerY, p[0] - centerX)))
                .map(CardQuadOrdering::copy)
                .toList();
    }
}
