package com.catcheck.scan.domain.color;

public record DeltaE2000Params(double kL, double kC, double kH) {

    public static final DeltaE2000Params STANDARD = new DeltaE2000Params(1.0, 1.0, 1.0);

    public static final DeltaE2000Params CAT_CHECK = new DeltaE2000Params(2.0, 1.0, 1.0);

    public DeltaE2000Params {
        if (!(kL > 0.0) || !(kC > 0.0) || !(kH > 0.0)) {
            throw new IllegalArgumentException("kL, kC, kH phai > 0 nhung gia tri: " + kL + ", " + kC + ", " + kH);
        }
    }
}
