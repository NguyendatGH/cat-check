package com.catcheck.export.application;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Vẽ biểu đồ pH theo thời gian dạng SVG thuần (string template, không dùng thư viện chart) —
 * đúng phương án p13 §13.7. Dải tham chiếu 6.3–6.6 tô nền nhạt xuyên suốt; điểm ngoài dải dùng
 * KÝ HIỆU khác (tam giác) chứ không chỉ đổi màu (p13 §13.5 — không truyền đạt phân loại chỉ bằng
 * màu, quan trọng khi in đen trắng).
 *
 * <p>Ranh giới 6.3/6.6 hard-code tạm ở đây: {@code export} không phụ thuộc
 * {@code scan.domain.color}/{@code colorchart} (xem {@code docs/handovers/A6.md}) nên không đọc
 * được {@code ph_classification_band} động; đây đúng bằng hằng số mà p13 tự trích dẫn trực tiếp
 * trong văn bản của chính nó (§13.3 Khối 4, §13.5), không phải số tự bịa.</p>
 */
final class SvgTrendChartBuilder {

    private static final double REF_LOW = 6.3;
    private static final double REF_HIGH = 6.6;
    private static final int WIDTH = 680;
    private static final int HEIGHT = 260;
    private static final int PAD_LEFT = 40;
    private static final int PAD_RIGHT = 16;
    private static final int PAD_TOP = 16;
    private static final int PAD_BOTTOM = 32;

    private SvgTrendChartBuilder() {
    }

    record Point(LocalDate date, double phValue, boolean outOfRange) {
    }

    static String build(List<Point> points) {
        if (points.isEmpty()) {
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + WIDTH + "\" height=\"" + HEIGHT + "\"></svg>";
        }
        double minPh = points.stream().mapToDouble(Point::phValue).min().orElse(REF_LOW) - 0.3;
        double maxPh = points.stream().mapToDouble(Point::phValue).max().orElse(REF_HIGH) + 0.3;
        minPh = Math.min(minPh, REF_LOW - 0.2);
        maxPh = Math.max(maxPh, REF_HIGH + 0.2);

        LocalDate firstDate = points.getFirst().date();
        LocalDate lastDate = points.getLast().date();
        long spanDays = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(firstDate, lastDate));

        int plotWidth = WIDTH - PAD_LEFT - PAD_RIGHT;
        int plotHeight = HEIGHT - PAD_TOP - PAD_BOTTOM;

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(WIDTH).append(' ').append(HEIGHT)
                .append("\" width=\"100%\" height=\"auto\" font-family=\"DejaVu Sans, sans-serif\">");

        // Nền trắng.
        svg.append("<rect x=\"0\" y=\"0\" width=\"").append(WIDTH).append("\" height=\"").append(HEIGHT)
                .append("\" fill=\"#ffffff\"/>");

        // Dải tham chiếu 6.3-6.6: nền nhạt + viền nét đứt (giữ ý nghĩa khi in đen trắng, p13 §13.5).
        double yRefTop = yOf(REF_HIGH, minPh, maxPh, plotHeight);
        double yRefBottom = yOf(REF_LOW, minPh, maxPh, plotHeight);
        svg.append("<rect x=\"").append(PAD_LEFT).append("\" y=\"").append(round(yRefTop))
                .append("\" width=\"").append(plotWidth).append("\" height=\"").append(round(yRefBottom - yRefTop))
                .append("\" fill=\"#e3f4ec\" stroke=\"#7fbf9e\" stroke-dasharray=\"4,3\"/>");

        // Trục Y: nhãn min/ref/max.
        for (double tick : new double[] {minPh, REF_LOW, REF_HIGH, maxPh}) {
            double y = yOf(tick, minPh, maxPh, plotHeight);
            svg.append("<line x1=\"").append(PAD_LEFT).append("\" y1=\"").append(round(y))
                    .append("\" x2=\"").append(WIDTH - PAD_RIGHT).append("\" y2=\"").append(round(y))
                    .append("\" stroke=\"#e5e7eb\" stroke-width=\"1\"/>");
            svg.append("<text x=\"2\" y=\"").append(round(y + 3)).append("\" font-size=\"9\" fill=\"#374151\">")
                    .append(String.format(Locale.US, "%.1f", tick)).append("</text>");
        }

        // Trục X: ngày đầu/cuối.
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        svg.append("<text x=\"").append(PAD_LEFT).append("\" y=\"").append(HEIGHT - 8)
                .append("\" font-size=\"9\" fill=\"#374151\">").append(firstDate.format(fmt)).append("</text>");
        svg.append("<text x=\"").append(WIDTH - PAD_RIGHT - 30).append("\" y=\"").append(HEIGHT - 8)
                .append("\" font-size=\"9\" fill=\"#374151\">").append(lastDate.format(fmt)).append("</text>");

        // Đường nối các điểm.
        StringBuilder polyline = new StringBuilder();
        for (Point p : points) {
            double x = xOf(p.date(), firstDate, spanDays, plotWidth);
            double y = yOf(p.phValue(), minPh, maxPh, plotHeight);
            polyline.append(round(x)).append(',').append(round(y)).append(' ');
        }
        svg.append("<polyline points=\"").append(polyline.toString().trim())
                .append("\" fill=\"none\" stroke=\"#1f6f4a\" stroke-width=\"2\"/>");

        // Điểm dữ liệu: hình tròn (trong dải) hoặc tam giác (ngoài dải) - khác biệt cả hình dạng.
        for (Point p : points) {
            double x = xOf(p.date(), firstDate, spanDays, plotWidth);
            double y = yOf(p.phValue(), minPh, maxPh, plotHeight);
            if (p.outOfRange()) {
                double half = 5;
                svg.append("<polygon points=\"")
                        .append(round(x)).append(',').append(round(y - half)).append(' ')
                        .append(round(x - half)).append(',').append(round(y + half)).append(' ')
                        .append(round(x + half)).append(',').append(round(y + half))
                        .append("\" fill=\"#b45309\" stroke=\"#ffffff\" stroke-width=\"1\"/>");
            } else {
                svg.append("<circle cx=\"").append(round(x)).append("\" cy=\"").append(round(y))
                        .append("\" r=\"4\" fill=\"#1f6f4a\" stroke=\"#ffffff\" stroke-width=\"1\"/>");
            }
        }

        svg.append("</svg>");
        return svg.toString();
    }

    private static double xOf(LocalDate date, LocalDate first, long spanDays, int plotWidth) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(first, date);
        return PAD_LEFT + (plotWidth * ((double) days / spanDays));
    }

    private static double yOf(double ph, double min, double max, int plotHeight) {
        double ratio = (ph - min) / (max - min);
        return PAD_TOP + plotHeight * (1 - ratio);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
