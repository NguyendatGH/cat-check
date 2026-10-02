package com.catcheck.colorchart.application;

import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.ColorChartPoint;
import com.catcheck.scan.domain.color.DeltaE2000;
import com.catcheck.scan.domain.color.DeltaE2000Params;
import com.catcheck.scan.domain.color.Lab;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kiểm tra điều kiện publish bảng màu (p6 §6.6.2) — chặn ở tầng service, không chỉ ở UI.
 *
 * <p>Bốn điều kiện, tất cả đều bắt buộc:
 * <ol>
 *   <li>Bảng {@code ACTIVE} phải có <b>≥ 4 mức</b>.</li>
 *   <li>Phải <b>phủ cả 6.3 và 6.6</b> — hai mốc phân loại của quyết định #6.</li>
 *   <li>Hai mức liền kề phải cách nhau <b>≥ 3 ΔE00</b> — nếu gần hơn thì camera không phân
 *       biệt được.</li>
 *   <li>{@code sort_order} phải cùng chiều với {@code ph_value}.</li>
 * </ol>
 *
 * <p>ΔE00 lấy từ {@code scan.domain.color} — module colorchart phụ thuộc scan (xem
 * {@code package-info}). Tham số kL/kC/kH đọc từ {@code color_chart.params} (JSONB) để
 * validation dùng <b>đúng</b> bộ tham số mà pipeline sẽ dùng, không phải bộ riêng.
 */
@Component
public class ChartPublishValidator {

    /** Số mức tối thiểu (p6 §6.6.2). */
    public static final int MIN_POINTS = 4;

    /** Khoảng cách ΔE00 tối thiểu giữa hai mức liền kề (p6 §6.6.2). */
    public static final double MIN_ADJACENT_DELTA_E00 = 3.0;

    /** Hai mốc phân loại bắt buộc phải phủ (quyết định #6). */
    public static final double REQUIRED_PH_LOW = 6.3;
    public static final double REQUIRED_PH_HIGH = 6.6;

    /**
     * Kết quả kiểm tra.
     *
     * @param valid      bảng đủ điều kiện publish
     * @param violations danh sách lỗi (rỗng nếu {@code valid})
     */
    public record Result(boolean valid, List<String> violations) {

        public static Result ok() {
            return new Result(true, List.of());
        }

        public static Result fail(List<String> violations) {
            return new Result(false, violations);
        }
    }

    /**
     * Kiểm tra một bảng màu trước khi publish.
     *
     * @param chart  bảng cần kiểm (phải ở trạng thái {@code DRAFT})
     * @param points các mức pH, đã sắp theo {@code sort_order}
     */
    public Result validate(ColorChart chart, List<ColorChartPoint> points) {
        List<String> violations = new ArrayList<>();

        if (chart.getStatus() != com.catcheck.colorchart.domain.ChartStatus.DRAFT) {
            violations.add("CHART_NOT_DRAFT");
        }

        if (points.size() < MIN_POINTS) {
            violations.add("TOO_FEW_POINTS");
        }

        boolean coversLow = points.stream()
                .anyMatch(p -> p.getPhValue().doubleValue() == REQUIRED_PH_LOW);
        boolean coversHigh = points.stream()
                .anyMatch(p -> p.getPhValue().doubleValue() == REQUIRED_PH_HIGH);
        if (!coversLow) {
            violations.add("MISSING_PH_6_3");
        }
        if (!coversHigh) {
            violations.add("MISSING_PH_6_6");
        }

        // sort_order phải đơn điệu theo ph_value (ràng buộc nghiệp vụ, không ép bằng CHECK đơn dòng).
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).getPhValue().compareTo(points.get(i - 1).getPhValue()) <= 0) {
                violations.add("SORT_ORDER_NOT_MONOTONIC");
                break;
            }
        }

        DeltaE2000Params params = parseParams(chart);
        for (int i = 0; i + 1 < points.size(); i++) {
            Lab lab1 = toLab(points.get(i));
            Lab lab2 = toLab(points.get(i + 1));
            double deltaE = DeltaE2000.deltaE(lab1, lab2, params);
            if (deltaE < MIN_ADJACENT_DELTA_E00) {
                violations.add("ADJACENT_DELTA_E_TOO_SMALL");
                break;
            }
        }

        return violations.isEmpty() ? Result.ok() : Result.fail(violations);
    }

    /**
     * Đọc bộ tham số ΔE00 từ {@code color_chart.params}. Mặc định của sản phẩm là
     * {@code kL=2, kC=1, kH=1} (p6 §6.5.1 S8) — nếu JSONB thiếu khoá thì dùng mặc định này.
     */
    public DeltaE2000Params parseParams(ColorChart chart) {
        Map<String, Object> params = chart.getParams();
        if (params == null) {
            return DeltaE2000Params.CAT_CHECK;
        }
        try {
            double kL = readDouble(params, "kL", DeltaE2000Params.CAT_CHECK.kL());
            double kC = readDouble(params, "kC", DeltaE2000Params.CAT_CHECK.kC());
            double kH = readDouble(params, "kH", DeltaE2000Params.CAT_CHECK.kH());
            return new DeltaE2000Params(kL, kC, kH);
        } catch (RuntimeException ex) {
            return DeltaE2000Params.CAT_CHECK;
        }
    }

    private double readDouble(Map<String, Object> params, String key, double fallback) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private Lab toLab(ColorChartPoint point) {
        return new Lab(
                point.getLabL().doubleValue(),
                point.getLabA().doubleValue(),
                point.getLabB().doubleValue());
    }
}
