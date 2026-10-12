package com.catcheck.scan.domain.color;

public record Lab(double l, double a, double b) {

    public static final double MIN_L = 0.0;
    public static final double MAX_L = 100.0;

    public Lab {
        if (Double.isNaN(l) || Double.isNaN(a) || Double.isNaN(b)) {
            throw new IllegalArgumentException("Gia tri Lab khong hop le (NaN): " + l + ", " + a + ", " + b);
        }
    }

    public static Lab of(double[] lab) {
        if (lab == null || lab.length < 3) {
            throw new IllegalArgumentException("Can mang Lab du 3 phan tu [L,a,b]");
        }
        return new Lab(lab[0], lab[1], lab[2]);
    }

    public double chroma() {
        return Math.hypot(a, b);
    }

    public double hueDegrees() {
        double h = Math.toDegrees(Math.atan2(b, a));
        return h < 0.0 ? h + 360.0 : h;
    }

    public double distanceTo(Lab other) {
        double dl = l - other.l;
        double da = a - other.a;
        double db = b - other.b;
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    public static Lab midpoint(Lab p, Lab q, double t) {
        return new Lab(
                p.l + t * (q.l - p.l),
                p.a + t * (q.a - p.a),
                p.b + t * (q.b - p.b));
    }

    public Lab plus(double dl, double da, double db) {
        return new Lab(l + dl, a + da, b + db);
    }

    public Lab times(double factor) {
        return new Lab(l * factor, a * factor, b * factor);
    }

    public double[] toArray() {
        return new double[] {l, a, b};
    }

    @Override
    public String toString() {
        return "Lab[L=" + l + ", a=" + a + ", b=" + b + "]";
    }
}
