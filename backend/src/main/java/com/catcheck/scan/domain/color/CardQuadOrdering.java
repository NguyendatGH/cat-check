package com.catcheck.scan.domain.color;

import com.catcheck.scan.domain.color.port.VisionEngine.QuadHint;

import java.util.Comparator;
import java.util.List;

/**
 * Chuẩn hoá bốn đỉnh thẻ về thứ tự bắt buộc: <b>trái trên → phải trên → phải dưới → trái dưới</b>.
 *
 * <h2>Vì sao phải là một bước riêng, không làm inline</h2>
 * <p>Contour trả về bốn điểm theo thứ tự nào tuỳ thuận toán (thường là ngược chiều kim đồng hồ,
 * bắt đầu từ điểm có giá trị nhỏ nhất). ArUco trả về đúng thứ tự. Nếu đưa thẳng điểm contour vào
 * phép nắn phẳng mà không sắp lại, ảnh ra vẫn <em>trông đúng</em> — nhưng mọi ô màu bị gán sang ô
 * khác: một ô 6,3 đọc thành ô 7,5 và báo pH sai hoàn toàn mà không có lỗi nào được ghi ra.
 * Lỗi kiểu này sống sót qua mọi review mã và chỉ lộ ra khi người dùng thấy kết quả vô lý.
 *
 * <p>Vì vậy quy tắc ở đây <b>thuần toán và kiểm thử được</b>, không cần thư viện native.
 */
public final class CardQuadOrdering {

    private CardQuadOrdering() {
        throw new AssertionError("CardQuadOrdering la lop tien ich, khong instantiate");
    }

    /**
     * Sắp xếp bốn điểm bất kỳ về thứ tự chuẩn.
     *
     * <p>Thuật toán: xếp bốn điểm theo góc quanh tâm rồi xoay vòng để điểm đầu là góc có
     * {@code x + y} nhỏ nhất. Trong toạ độ ảnh (y xuống), góc tăng dần đi theo chiều kim đồng hồ
     * trên màn hình, nên sau góc trên-trái lần lượt là trên-phải, dưới-phải, dưới-trái.
     *
     * <p>Vì sao không dùng "điểm gần tâm nhất": phép đó <b>không xác định được góc</b>. Với tứ giác
     * nghiêng, điểm gần tâm nhất có thể là bất kỳ đỉnh nào, và hậu quả là hoán đổi hai ô màu ở góc —
     * ảnh vẫn ra hình đúng nhưng hai ô ở hai đầu bị đảo màu. Cách duy nhất đúng là dựa vào thứ tự vòng
     * tròn.
     *
     * <p>Ổn định với góc nghiêng tới 45°. Quá đó thì thẻ đã không nằm trong khung chụp hợp lệ và nên
     * bị từ chối ở tầng chất lượng ảnh, không phải sửa ở đây.
     *
     * @param points bốn cặp {@code [x, y]}, thứ tự vào tùy ý
     */
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

    /** Diện tích đa giác bốn đỉnh (Shoelace) — dùng để loại thẻ quá nhỏ hoặc tự giao cắt. */
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

    /** Thẻ phải vuông vắn và đủ lớn; nếu không thì phép nắn phẳng sẽ kéo dài và méo màu. */
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
        // Tỉ lệ cạnh lệch quá 2.5 nghĩa là tứ giác bị méo nặng, gần như chắc chắn là phát hiện sai.
        double ratio = width / height;
        return ratio > 0.4 && ratio < 2.5;
    }

    /** Diện tích tối thiểu cho phép (px²) — chặn thẻ 2×2 px biến thành nhiễu tìm được. */
    public static final int MIN_CARD_AREA_PX = 20_000;

    private static double[] copy(double[] point) {
        return new double[] {point[0], point[1]};
    }

    private static double distance(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    /** Sắp xếp bốn điểm theo góc quanh tâm — dùng khi cần thứ tự vòng tròn trước khi chuẩn hoá. */
    public static List<double[]> sortAroundCenter(List<double[]> points) {
        double centerX = points.stream().mapToDouble(p -> p[0]).average().orElseThrow();
        double centerY = points.stream().mapToDouble(p -> p[1]).average().orElseThrow();
        return points.stream()
                .sorted(Comparator.comparingDouble(p -> Math.atan2(p[1] - centerY, p[0] - centerX)))
                .map(CardQuadOrdering::copy)
                .toList();
    }
}
