package com.catcheck.scan.domain.color;

import java.util.List;

public final class CcmSolver {

    private static final double RANKING_TOLERANCE = 1e-9;

    private static final double WHITE_BALANCE_TOLERANCE = 0.02;

    private CcmSolver() {
        throw new AssertionError("CcmSolver la lop tien ich, khong instantiate");
    }

    public static Matrix3 solve(List<ColorSamples.Sample> samples) {
        if (samples == null || samples.size() < 3) {
            throw new IllegalArgumentException("Can it nhat 3 mau de uoc luong CCM 3x3, nhan duoc "
                    + (samples == null ? 0 : samples.size()));
        }

        double[][] normal = new double[3][6];
        int used = 0;

        for (ColorSamples.Sample sample : samples) {
            if (sample.weight() <= 0.0) {
                continue;
            }
            used++;
            double w = sample.weight();
            double[] measured = {sample.measured().r(), sample.measured().g(), sample.measured().b()};
            double[] reference = {sample.reference().r(), sample.reference().g(), sample.reference().b()};

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    normal[row][col] += w * measured[row] * measured[col];
                }
                for (int channel = 0; channel < 3; channel++) {
                    normal[row][3 + channel] += w * measured[row] * reference[channel];
                }
            }
        }

        if (used < 3) {
            throw new IllegalArgumentException("So mau co trong so > 0 phai >= 3, nhan duoc " + used);
        }

        eliminate(normal);
        double[] red = backSubstitute(normal, 3);
        double[] green = backSubstitute(normal, 4);
        double[] blue = backSubstitute(normal, 5);

        return new Matrix3(
                red[0], red[1], red[2],
                green[0], green[1], green[2],
                blue[0], blue[1], blue[2]);
    }

    public static Matrix3 solveCentered(List<ColorSamples.Sample> samples) {
        RgbLinear measuredMean = mean(samples, true);
        RgbLinear referenceMean = mean(samples, false);

        var shifted = new java.util.ArrayList<ColorSamples.Sample>(samples.size());
        for (ColorSamples.Sample sample : samples) {
            shifted.add(new ColorSamples.Sample(
                    new RgbLinear(sample.measured().r() - measuredMean.r(),
                            sample.measured().g() - measuredMean.g(),
                            sample.measured().b() - measuredMean.b()),
                    new RgbLinear(sample.reference().r() - referenceMean.r(),
                            sample.reference().g() - referenceMean.g(),
                            sample.reference().b() - referenceMean.b()),
                    sample.sigma(),
                    sample.weight()));
        }

        Matrix3 linear = solve(shifted);
        double[] residual = linear.apply(new double[] {measuredMean.r(), measuredMean.g(), measuredMean.b()});
        double offsetMagnitude = Math.hypot(
                Math.hypot(residual[0] - referenceMean.r(),
                        residual[1] - referenceMean.g()),
                residual[2] - referenceMean.b());
        if (offsetMagnitude > WHITE_BALANCE_TOLERANCE) {
            throw new IllegalStateException(String.format(
                    "Phan lech giua mau trung binh con lai %.4f (> %.4f) — can khu trang truoc khi uoc luong CCM",
                    offsetMagnitude, WHITE_BALANCE_TOLERANCE));
        }
        return linear;
    }

    private static RgbLinear mean(List<ColorSamples.Sample> samples, boolean measured) {
        double sr = 0.0;
        double sg = 0.0;
        double sb = 0.0;
        int n = 0;
        for (ColorSamples.Sample sample : samples) {
            RgbLinear v = measured ? sample.measured() : sample.reference();
            sr += v.r();
            sg += v.g();
            sb += v.b();
            n++;
        }
        if (n == 0) {
            throw new IllegalArgumentException("Khong co mau nao de tinh trung binh");
        }
        return new RgbLinear(sr / n, sg / n, sb / n);
    }

    private static void eliminate(double[][] augmented) {
        for (int pivot = 0; pivot < 3; pivot++) {
            int best = pivot;
            for (int row = pivot + 1; row < 3; row++) {
                if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[best][pivot])) {
                    best = row;
                }
            }
            if (Math.abs(augmented[best][pivot]) < RANKING_TOLERANCE) {
                throw new IllegalArgumentException(
                        "He phuong trinh CCM suy thoang (cot " + pivot + " khong doc lap) — "
                                + "du lieu mau khong du phu tap khac nhau");
            }
            double[] swap = augmented[pivot];
            augmented[pivot] = augmented[best];
            augmented[best] = swap;

            for (int row = pivot + 1; row < 3; row++) {
                double factor = augmented[row][pivot] / augmented[pivot][pivot];
                for (int col = pivot; col < augmented[pivot].length; col++) {
                    augmented[row][col] -= factor * augmented[pivot][col];
                }
            }
        }
    }

    private static double[] backSubstitute(double[][] augmented, int rhsColumn) {
        double[] x = new double[3];
        for (int row = 2; row >= 0; row--) {
            double sum = augmented[row][rhsColumn];
            for (int col = row + 1; col < 3; col++) {
                sum -= augmented[row][col] * x[col];
            }
            x[row] = sum / augmented[row][row];
        }
        return x;
    }
}
