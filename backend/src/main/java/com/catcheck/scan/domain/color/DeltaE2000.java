package com.catcheck.scan.domain.color;

public final class DeltaE2000 {

    private static final double POW_25_7 = 6103515625.0;

    private DeltaE2000() {
        throw new AssertionError("DeltaE2000 la lop tien ich, khong instantiate");
    }

    public static double deltaE(Lab a, Lab b) {
        return deltaE(a, b, DeltaE2000Params.CAT_CHECK);
    }

    public static double deltaE(Lab a, Lab b, DeltaE2000Params params) {
        double l1 = a.l();
        double l2 = b.l();
        double a1 = a.a();
        double a2 = b.a();
        double b1 = a.b();
        double b2 = b.b();

        double c1Star = Math.hypot(a1, b1);
        double c2Star = Math.hypot(a2, b2);
        double cBarStar = (c1Star + c2Star) / 2.0;

        double cBarStar7 = Math.pow(cBarStar, 7.0);
        double g = 0.5 * (1.0 - Math.sqrt(cBarStar7 / (cBarStar7 + POW_25_7)));

        double a1Prime = (1.0 + g) * a1;
        double a2Prime = (1.0 + g) * a2;
        double c1Prime = Math.hypot(a1Prime, b1);
        double c2Prime = Math.hypot(a2Prime, b2);

        double h1Prime = hueAngle(b1, a1Prime);
        double h2Prime = hueAngle(b2, a2Prime);

        double deltaLPrime = l2 - l1;
        double deltaCPrime = c2Prime - c1Prime;

        double cBarPrime = (c1Prime + c2Prime) / 2.0;
        double hBarPrime = hueMean(h1Prime, h2Prime, c1Prime, c2Prime);

        double deltaBigHuePrime;
        if (c1Prime * c2Prime == 0.0) {
            deltaBigHuePrime = 0.0;
        } else if (Math.abs(h2Prime - h1Prime) <= 180.0) {
            deltaBigHuePrime = h2Prime - h1Prime;
        } else if (h2Prime - h1Prime > 180.0) {
            deltaBigHuePrime = h2Prime - h1Prime - 360.0;
        } else {
            deltaBigHuePrime = h2Prime - h1Prime + 360.0;
        }
        double deltaHPrime = 2.0 * Math.sqrt(c1Prime * c2Prime)
                * Math.sin(toRadians(deltaBigHuePrime) / 2.0);

        double lBarPrime = (l1 + l2) / 2.0;

        double t = 1.0
                - 0.17 * Math.cos(toRadians(hBarPrime - 30.0))
                + 0.24 * Math.cos(toRadians(2.0 * hBarPrime))
                + 0.32 * Math.cos(toRadians(3.0 * hBarPrime + 6.0))
                - 0.20 * Math.cos(toRadians(4.0 * hBarPrime - 63.0));

        double deltaTheta = 30.0 * Math.exp(-Math.pow((hBarPrime - 275.0) / 25.0, 2.0));
        double cBarPrime7 = Math.pow(cBarPrime, 7.0);
        double rC = 2.0 * Math.sqrt(cBarPrime7 / (cBarPrime7 + POW_25_7));
        double lBarPrimeMinus50Squared = (lBarPrime - 50.0) * (lBarPrime - 50.0);
        double sC = 1.0 + 0.045 * cBarPrime;
        double sL = 1.0 + 0.015 * lBarPrimeMinus50Squared / Math.sqrt(20.0 + lBarPrimeMinus50Squared);
        double sH = 1.0 + 0.015 * cBarPrime * t;
        double rT = -Math.sin(toRadians(2.0 * deltaTheta)) * rC;

        double termL = deltaLPrime / (params.kL() * sL);
        double termC = deltaCPrime / (params.kC() * sC);
        double termH = deltaHPrime / (params.kH() * sH);
        double squared = termL * termL + termC * termC + termH * termH + rT * termC * termH;
        return squared <= 0.0 ? 0.0 : Math.sqrt(squared);
    }

    public static double deltaE(double[] lab1, double[] lab2, DeltaE2000Params params) {
        return deltaE(Lab.of(lab1), Lab.of(lab2), params);
    }

    public static double deltaERelative(Lab sample, Lab cardPatch, DeltaE2000Params params) {
        return deltaE(sample, cardPatch, params);
    }

    private static double hueAngle(double bStar, double aPrime) {
        if (aPrime == 0.0 && bStar == 0.0) {
            return 0.0;
        }
        double degrees = Math.toDegrees(Math.atan2(bStar, aPrime));
        return degrees < 0.0 ? degrees + 360.0 : degrees;
    }

    private static double hueMean(double h1, double h2, double c1, double c2) {
        if (c1 * c2 == 0.0) {
            return h1 + h2;
        }
        double diff = Math.abs(h1 - h2);
        if (diff <= 180.0) {
            return (h1 + h2) / 2.0;
        }
        if (h1 + h2 < 360.0) {
            return (h1 + h2 + 360.0) / 2.0;
        }
        return (h1 + h2 - 360.0) / 2.0;
    }

    private static double toRadians(double degrees) {
        return degrees * Math.PI / 180.0;
    }
}
