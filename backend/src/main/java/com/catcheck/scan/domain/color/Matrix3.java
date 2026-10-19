package com.catcheck.scan.domain.color;

public record Matrix3(
        double m00, double m01, double m02,
        double m10, double m11, double m12,
        double m20, double m21, double m22) {

    public static final Matrix3 IDENTITY = new Matrix3(1, 0, 0, 0, 1, 0, 0, 0, 1);

    public Matrix3 {
        for (double v : new double[] {m00, m01, m02, m10, m11, m12, m20, m21, m22}) {
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                throw new IllegalArgumentException("Phan tu ma tran khong hop le: " + v);
            }
        }
    }

    public static Matrix3 ofArray(double[] values) {
        if (values == null || values.length != 9) {
            throw new IllegalArgumentException("CCM phai la mang dung 9 phan tu theo hang");
        }
        return new Matrix3(values[0], values[1], values[2],
                values[3], values[4], values[5],
                values[6], values[7], values[8]);
    }

    public double[] toArray() {
        return new double[] {m00, m01, m02, m10, m11, m12, m20, m21, m22};
    }

    public double[] apply(double[] vector) {
        if (vector == null || vector.length != 3) {
            throw new IllegalArgumentException("Vector phai co dung 3 phan tu");
        }
        return new double[] {
                m00 * vector[0] + m01 * vector[1] + m02 * vector[2],
                m10 * vector[0] + m11 * vector[1] + m12 * vector[2],
                m20 * vector[0] + m21 * vector[1] + m22 * vector[2]
        };
    }

    public RgbLinear apply(RgbLinear rgb) {
        return new RgbLinear(
                m00 * rgb.r() + m01 * rgb.g() + m02 * rgb.b(),
                m10 * rgb.r() + m11 * rgb.g() + m12 * rgb.b(),
                m20 * rgb.r() + m21 * rgb.g() + m22 * rgb.b());
    }

    public Matrix3 multiply(Matrix3 other) {
        return new Matrix3(
                m00 * other.m00 + m01 * other.m10 + m02 * other.m20,
                m00 * other.m01 + m01 * other.m11 + m02 * other.m21,
                m00 * other.m02 + m01 * other.m12 + m02 * other.m22,
                m10 * other.m00 + m11 * other.m10 + m12 * other.m20,
                m10 * other.m01 + m11 * other.m11 + m12 * other.m21,
                m10 * other.m02 + m11 * other.m12 + m12 * other.m22,
                m20 * other.m00 + m21 * other.m10 + m22 * other.m20,
                m20 * other.m01 + m21 * other.m11 + m22 * other.m21,
                m20 * other.m02 + m21 * other.m12 + m22 * other.m22);
    }

    public Matrix3 transpose() {
        return new Matrix3(m00, m10, m20, m01, m11, m21, m02, m12, m22);
    }

    public double determinant() {
        return m00 * (m11 * m22 - m12 * m21)
                - m01 * (m10 * m22 - m12 * m20)
                + m02 * (m10 * m21 - m11 * m20);
    }

    public Matrix3 inverse() {
        double det = determinant();
        if (Math.abs(det) < 1e-12) {
            throw new IllegalStateException("Ma tran khong khat; khong the nghich dao (det=" + det + ")");
        }
        double inv = 1.0 / det;
        return new Matrix3(
                (m11 * m22 - m12 * m21) * inv,
                (m02 * m21 - m01 * m22) * inv,
                (m01 * m12 - m02 * m11) * inv,
                (m12 * m20 - m10 * m22) * inv,
                (m00 * m22 - m02 * m20) * inv,
                (m02 * m10 - m00 * m12) * inv,
                (m10 * m21 - m11 * m20) * inv,
                (m01 * m20 - m00 * m21) * inv,
                (m00 * m11 - m01 * m10) * inv);
    }

    public double relativeErrorTo(Matrix3 reference) {
        double diff = 0.0;
        double norm = 0.0;
        double[] a = toArray();
        double[] b = reference.toArray();
        for (int i = 0; i < 9; i++) {
            diff += (a[i] - b[i]) * (a[i] - b[i]);
            norm += b[i] * b[i];
        }
        return norm == 0.0 ? Math.sqrt(diff) : Math.sqrt(diff / norm);
    }

    public double maxAbsElement() {
        double max = 0.0;
        for (double v : toArray()) {
            max = Math.max(max, Math.abs(v));
        }
        return max;
    }

    @Override
    public String toString() {
        return String.format("Matrix3[%.6f %.6f %.6f; %.6f %.6f %.6f; %.6f %.6f %.6f]",
                m00, m01, m02, m10, m11, m12, m20, m21, m22);
    }
}
