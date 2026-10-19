package com.catcheck.scan.domain.color;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class RobustStats {

    public static final double OUTLIER_K = 2.5;

    public static final double TRIM = 0.10;

    public static final int MIN_BLOBS = 4;

    private RobustStats() {
        throw new AssertionError("RobustStats la lop tien ich, khong instantiate");
    }

    public record Result(
            Lab median,
            int blobCount,
            int droppedCount,
            double spreadDeltaE00,
            boolean fewBlobs,
            boolean mixedColors,
            double madDistance) {

        public boolean looksMixed() {
            return mixedColors;
        }
    }

    public static Result analyze(List<Lab> samples, double mixedFlag) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Can it nhat mot mau de thong ke");
        }

        Lab center0 = medianPerChannel(samples);
        double[] distances = distancesTo(samples, center0);
        double medianDistance = median(distances);
        double madD = 1.4826 * medianAbsoluteDeviation(distances, medianDistance);
        double threshold = medianDistance + OUTLIER_K * madD;

        List<Lab> kept = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            if (distances[i] <= threshold) {
                kept.add(samples.get(i));
            }
        }

        boolean fewBlobs = kept.size() < MIN_BLOBS;
        Lab median = fewBlobs ? center0 : trimmedMeanPerChannel(kept, TRIM);

        double spread = median(distancesTo(kept, median));

        return new Result(median, kept.size(), samples.size() - kept.size(), spread, fewBlobs,
                spread > mixedFlag, madD);
    }

    public static Result analyze(List<Lab> samples) {
        return analyze(samples, 6.0);
    }

    private static double[] distancesTo(List<Lab> samples, Lab center) {
        double[] distances = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            distances[i] = DeltaE2000.deltaE(samples.get(i), center, DeltaE2000Params.CAT_CHECK);
        }
        return distances;
    }

    private static double medianAbsoluteDeviation(double[] values, double center) {
        double[] deviations = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            deviations[i] = Math.abs(values[i] - center);
        }
        return median(deviations);
    }

    public static List<Lab> trimOutliers(List<Lab> samples) {
        if (samples.size() < MIN_BLOBS) {
            return List.copyOf(samples);
        }
        Lab center0 = medianPerChannel(samples);
        double[] distances = distancesTo(samples, center0);
        double medianDistance = median(distances);
        double threshold = medianDistance
                + OUTLIER_K * (1.4826 * medianAbsoluteDeviation(distances, medianDistance));

        List<Lab> kept = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            if (distances[i] <= threshold) {
                kept.add(samples.get(i));
            }
        }
        return kept.size() < MIN_BLOBS ? List.copyOf(samples) : List.copyOf(kept);
    }

    public static Lab trimmedMeanPerChannel(List<Lab> samples, double trim) {
        double[] l = new double[samples.size()];
        double[] a = new double[samples.size()];
        double[] b = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            l[i] = samples.get(i).l();
            a[i] = samples.get(i).a();
            b[i] = samples.get(i).b();
        }
        return new Lab(trimmedMean(l, trim), trimmedMean(a, trim), trimmedMean(b, trim));
    }

    public static Lab medianPerChannel(Collection<Lab> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Khong co mau nao de tinh trung vi");
        }
        double[] l = new double[samples.size()];
        double[] a = new double[samples.size()];
        double[] b = new double[samples.size()];
        int i = 0;
        for (Lab sample : samples) {
            l[i] = sample.l();
            a[i] = sample.a();
            b[i] = sample.b();
            i++;
        }
        return new Lab(median(l), median(a), median(b));
    }

    public static double mad(Collection<Lab> samples, java.util.function.ToDoubleFunction<Lab> channel) {
        Lab center = medianPerChannel(samples);
        double[] deviations = new double[samples.size()];
        int i = 0;
        for (Lab sample : samples) {
            deviations[i] = Math.abs(channel.applyAsDouble(sample) - channel.applyAsDouble(center));
            i++;
        }
        return median(deviations);
    }

    public static double distanceMad(List<Lab> samples, Lab center) {
        double[] distances = distancesTo(samples, center);
        return 1.4826 * medianAbsoluteDeviation(distances, median(distances));
    }

    public static double trimmedMedian(double[] values, double trim) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Mang rong khong duoc null hoac rong");
        }
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int cut = (int) Math.floor(sorted.length * trim);
        if (sorted.length - 2 * cut < 1) {
            cut = 0;
        }
        return median(java.util.Arrays.copyOfRange(sorted, cut, sorted.length - cut));
    }

    public static double trimmedMean(double[] values, double trim) {
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int cut = (int) Math.floor(sorted.length * trim);
        if (sorted.length - 2 * cut < 1) {
            cut = 0;
        }
        double sum = 0.0;
        int n = sorted.length - 2 * cut;
        for (int i = cut; i < sorted.length - cut; i++) {
            sum += sorted[i];
        }
        return sum / n;
    }

    public static double median(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Mang rong khong duoc null hoac rong");
        }
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }
}
