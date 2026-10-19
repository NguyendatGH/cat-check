package com.catcheck.scan.domain.color;

public record Xyz(double x, double y, double z) {

    public static final Xyz D65_WHITE_POINT =
            new Xyz(0.4123907992659595 + 0.35758433938387796 + 0.1804807884018343,
                    0.21263900587151036 + 0.7151686787677559 + 0.07219231536073371,
                    0.019330818715591851 + 0.11919477979462599 + 0.9505321522496606);

    public static final Xyz D65_CIE_PUBLISHED = new Xyz(0.95047, 1.00000, 1.08883);

    public static final Xyz D65 = D65_WHITE_POINT;

    public Xyz {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            throw new IllegalArgumentException("Gia tri XYZ khong hop le (NaN)");
        }
    }

    public static Xyz ofArray(double[] xyz) {
        if (xyz == null || xyz.length < 3) {
            throw new IllegalArgumentException("Can mang XYZ du 3 phan tu [X,Y,Z]");
        }
        return new Xyz(xyz[0], xyz[1], xyz[2]);
    }

    public double component(int index) {
        return switch (index) {
            case 0 -> x;
            case 1 -> y;
            case 2 -> z;
            default -> throw new IllegalArgumentException("Chi so kenh XYZ phai la 0, 1 hoac 2");
        };
    }

    public double[] toArray() {
        return new double[] {x, y, z};
    }

    @Override
    public String toString() {
        return "Xyz[" + x + ", " + y + ", " + z + "]";
    }
}
