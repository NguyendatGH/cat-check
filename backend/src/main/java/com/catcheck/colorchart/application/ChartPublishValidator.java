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

@Component
public class ChartPublishValidator {

    public static final int MIN_POINTS = 4;

    public static final double MIN_ADJACENT_DELTA_E00 = 3.0;

    public static final double REQUIRED_PH_LOW = 6.3;
    public static final double REQUIRED_PH_HIGH = 6.6;

    public record Result(boolean valid, List<String> violations) {

        public static Result ok() {
            return new Result(true, List.of());
        }

        public static Result fail(List<String> violations) {
            return new Result(false, violations);
        }
    }

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
