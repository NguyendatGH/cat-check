package com.catcheck.scan.domain.color;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record ColorSamples(List<Sample> samples) {

    public ColorSamples {
        if (samples == null) {
            throw new IllegalArgumentException("Danh sach mau khong duoc null");
        }
        samples = List.copyOf(samples);
    }

    public static ColorSamples of(Collection<Sample> samples) {
        return new ColorSamples(new ArrayList<>(samples));
    }

    public int size() {
        return samples.size();
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }

    public record Sample(RgbLinear measured, RgbLinear reference, double sigma, double weight) {

        public Sample {
            requireFinite(measured, "measured");
            requireFinite(reference, "reference");
            if (!(sigma > 0.0) || Double.isNaN(sigma)) {
                throw new IllegalArgumentException("sigma phai > 0, nhan duoc " + sigma);
            }
            if (weight < 0.0 || Double.isNaN(weight)) {
                throw new IllegalArgumentException("weight phai >= 0, nhan duoc " + weight);
            }
        }

        public static Sample of(RgbLinear measured, RgbLinear reference) {
            return new Sample(measured, reference, 1.0 / 255.0, 1.0);
        }

        public static Sample excluded(RgbLinear measured, RgbLinear reference) {
            return new Sample(measured, reference, 1.0 / 255.0, 0.0);
        }

        public double weightFromSigma() {
            return 1.0 / (sigma * sigma);
        }
    }

    private static void requireFinite(RgbLinear rgb, String name) {
        if (rgb == null) {
            throw new IllegalArgumentException("Mau " + name + " khong duoc null");
        }
        if (Double.isNaN(rgb.r()) || Double.isNaN(rgb.g()) || Double.isNaN(rgb.b())) {
            throw new IllegalArgumentException("Mau " + name + " chua NaN");
        }
    }
}
