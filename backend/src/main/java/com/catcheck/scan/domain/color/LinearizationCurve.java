package com.catcheck.scan.domain.color;

public record LinearizationCurve(Type type, GrayPoly3 polynomial) {

    public enum Type {
        MEASURED,
        STANDARD_SRGB
    }

    public record GrayPoly3(double c0, double c1, double c2, double c3) {

        public double evaluate(double s) {
            return c0 + c1 * s + c2 * s * s + c3 * s * s * s;
        }

        public double derivative(double s) {
            return c1 + 2 * c2 * s + 3 * c3 * s * s;
        }

        public double[] toArray() {
            return new double[] {c0, c1, c2, c3};
        }
    }

    private static final int MONOTONIC_STEPS = 200;

    public LinearizationCurve {
        if (type == null) {
            throw new IllegalArgumentException("Loai duong cong khong duoc null");
        }
        if (type == Type.MEASURED && polynomial == null) {
            throw new IllegalArgumentException("Duong cong do duoc phai co da thuc bac 3");
        }
    }

    public static LinearizationCurve measured(GrayPoly3 polynomial) {
        return new LinearizationCurve(Type.MEASURED, polynomial);
    }

    public static LinearizationCurve standard() {
        return new LinearizationCurve(Type.STANDARD_SRGB, null);
    }

    public double linearize(double encoded) {
        return type == Type.STANDARD_SRGB
                ? ColorSpace.srgbToLinear(encoded)
                : polynomial.evaluate(encoded);
    }

    public boolean isMonotonic() {
        if (type == Type.STANDARD_SRGB) {
            return true;
        }
        for (int i = 0; i <= MONOTONIC_STEPS; i++) {
            double s = (double) i / MONOTONIC_STEPS;
            if (polynomial.derivative(s) < -1e-9) {
                return false;
            }
        }
        return true;
    }

    public static LinearizationCurve resolveOrStandard(GrayPoly3 measured) {
        if (measured == null) {
            return standard();
        }
        LinearizationCurve candidate = measured(measured);
        if (!candidate.isMonotonic()) {
            return standard();
        }
        for (int i = 0; i <= MONOTONIC_STEPS; i++) {
            double value = candidate.linearize((double) i / MONOTONIC_STEPS);
            if (value < -1e-6 || value > 1.0 + 1e-6) {
                return standard();
            }
        }
        return candidate;
    }

    public static final int MIN_GRAY_PATCHES = 4;

    public static GrayPoly3 fit(double[] encoded, double[] linearReferences) {
        if (encoded == null || linearReferences == null || encoded.length != linearReferences.length) {
            throw new IllegalArgumentException("Can hai mang cung do dai");
        }
        if (encoded.length < MIN_GRAY_PATCHES) {
            throw new IllegalArgumentException("Can it nhat " + MIN_GRAY_PATCHES
                    + " o xam de fit bac 3, nhan duoc " + encoded.length);
        }
        for (double s : encoded) {
            if (s < -1e-9 || s > 1.0 + 1e-9) {
                throw new IllegalArgumentException("Gia tri xam phai trong [0,1], nhan duoc " + s);
            }
        }

        int order = 4;
        double[][] a = new double[order][order + 1];
        for (int k = 0; k < order; k++) {
            for (int j = 0; j < order; j++) {
                a[k][j] = 0.0;
            }
            a[k][order] = 0.0;
        }
        for (int i = 0; i < encoded.length; i++) {
            double[] basis = {1.0, encoded[i], encoded[i] * encoded[i], encoded[i] * encoded[i] * encoded[i]};
            for (int k = 0; k < order; k++) {
                for (int j = 0; j < order; j++) {
                    a[k][j] += basis[k] * basis[j];
                }
                a[k][order] += basis[k] * linearReferences[i];
            }
        }

        double[] coeffs = solve(a, order);
        return new GrayPoly3(coeffs[0], coeffs[1], coeffs[2], coeffs[3]);
    }

    public static LinearizationCurve fitOrStandard(double[] encoded, double[] linearReferences) {
        try {
            return resolveOrStandard(fit(encoded, linearReferences));
        } catch (IllegalArgumentException e) {
            return standard();
        }
    }

    private static double[] solve(double[][] augmented, int n) {
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(augmented[row][col]) > Math.abs(augmented[pivot][col])) {
                    pivot = row;
                }
            }
            if (Math.abs(augmented[pivot][col]) < 1e-12) {
                throw new IllegalArgumentException(
                        "He phuong trinh fit bi ky — duong cong khong xac dinh, hay la o xam trung lap");
            }
            double[] swap = augmented[col];
            augmented[col] = augmented[pivot];
            augmented[pivot] = swap;

            for (int row = 0; row < n; row++) {
                if (row == col) {
                    continue;
                }
                double factor = augmented[row][col] / augmented[col][col];
                if (factor == 0.0) {
                    continue;
                }
                for (int k = col; k <= n; k++) {
                    augmented[row][k] -= factor * augmented[col][k];
                }
            }
        }
        double[] solution = new double[n];
        for (int i = 0; i < n; i++) {
            solution[i] = augmented[i][n] / augmented[i][i];
        }
        return solution;
    }
}
