package com.catcheck.scan.tooling;

import com.catcheck.scan.application.ColorPipelineService;
import com.catcheck.scan.application.PipelineOutcome;
import com.catcheck.scan.domain.color.CardLayoutCatalog;
import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.port.VisionEngine;
import com.catcheck.scan.domain.port.RawImagePort;
import com.catcheck.scan.infrastructure.vision.OpenCvNativeLoader;
import com.catcheck.scan.infrastructure.vision.OpenCvRawImageProcessor;
import com.catcheck.scan.infrastructure.vision.OpenCvVisionEngine;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class PipelineAccuracyHarness {

    private PipelineAccuracyHarness() {
    }

    record GroundTruthRow(String filename, double truePh, String phone, String lighting,
                           Integer minutesAfterWet, boolean hasCard) {
    }

    record SampleResult(String filename, double truePh, PhBandClassifier.Code trueBand,
                         Double predictedPh, PhBandClassifier.Code predictedBand,
                         boolean inconclusive, double confidence, long processingNanos,
                         String qualityFlags, String error) {

        boolean failed() {
            return error != null;
        }
    }

    record Report(int csvRows, int imagesFound, int missingImages, int failures, int evaluated,
                   int inconclusiveCount, Double mae, Double within025Pct, Double within05Pct,
                   Long p50Micros, Long p95Micros, Boolean chartIsPlaceholder,
                   Map<PhBandClassifier.Code, Map<PhBandClassifier.Code, Integer>> confusion,
                   List<SampleResult> samples, boolean noData) {

        static Report noData(int csvRows) {
            return new Report(csvRows, 0, 0, 0, 0, 0, null, null, null, null, null, null,
                    Map.of(), List.of(), true);
        }
    }


    static List<GroundTruthRow> readGroundTruth(Path csvPath) {
        if (csvPath == null || !Files.isRegularFile(csvPath)) {
            return List.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(csvPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<GroundTruthRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split(",", -1);
            if (cols.length < 6) {
                continue;
            }
            try {
                String filename = cols[0].strip();
                double truePh = Double.parseDouble(cols[1].strip());
                String phone = cols[2].strip();
                String lighting = cols[3].strip();
                String minutesRaw = cols[4].strip();
                Integer minutesAfterWet = minutesRaw.isEmpty() ? null : Integer.parseInt(minutesRaw);
                boolean hasCard = parseBool(cols[5].strip());
                rows.add(new GroundTruthRow(filename, truePh, phone, lighting, minutesAfterWet, hasCard));
            } catch (NumberFormatException e) {
            }
        }
        return rows;
    }

    private static boolean parseBool(String raw) {
        return raw.equalsIgnoreCase("true") || raw.equals("1") || raw.equalsIgnoreCase("yes");
    }

    private static boolean isEmptyOrMissingDir(Path dir) {
        if (!Files.isDirectory(dir)) {
            return true;
        }
        try (var entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    record Wiring(ColorPipelineService pipeline, ChartCatalog chartCatalog) {
    }

    static Wiring buildPipeline() {
        OpenCvNativeLoader nativeLoader = new OpenCvNativeLoader();
        invokePostConstruct(nativeLoader);
        if (!nativeLoader.isAvailable()) {
            throw new IllegalStateException(
                    "OpenCV native khong nap duoc - harness can native lib linux-x86_64 (xem "
                            + "OpenCvNativeLoader)");
        }
        RawImagePort rawImagePort = new OpenCvRawImageProcessor(nativeLoader);
        VisionEngine visionEngine = new OpenCvVisionEngine(nativeLoader);
        ChartCatalog chartCatalog = LocalDbChartCatalog.fromSystemPropertiesOrLocalDefaults();
        CardLayoutCatalog cardLayoutCatalog = code -> Optional.empty();
        var substrateProfileCatalog = (com.catcheck.scan.domain.color.SubstrateProfileCatalog)
                (line, batch) -> Optional.of(new Lab(66.814, 3.154, 10.461));
        ColorPipelineService pipeline = new ColorPipelineService(
                rawImagePort, visionEngine, chartCatalog, cardLayoutCatalog,
                substrateProfileCatalog, Clock.systemUTC());
        return new Wiring(pipeline, chartCatalog);
    }

    private static void invokePostConstruct(OpenCvNativeLoader loader) {
        try {
            Method load = OpenCvNativeLoader.class.getDeclaredMethod("load");
            load.setAccessible(true);
            load.invoke(loader);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Khong goi duoc OpenCvNativeLoader.load() qua reflection", e);
        }
    }


    static Report evaluate(Path imageDir, Path csvPath, PrintStream out) {
        List<GroundTruthRow> rows = readGroundTruth(csvPath);
        if (rows.isEmpty() || imageDir == null || isEmptyOrMissingDir(imageDir)) {
            out.println("0 anh, chua co du lieu that (thu muc anh hoac CSV ground truth rong/khong ton tai).");
            out.println("  thu muc anh: " + (imageDir == null ? "(null)" : imageDir.toAbsolutePath()));
            out.println("  CSV ground truth: " + (csvPath == null ? "(null)" : csvPath.toAbsolutePath()));
            return Report.noData(rows.size());
        }

        Wiring wiring = buildPipeline();
        List<PhBandClassifier.Band> bands = wiring.chartCatalog().findGlobalBands();

        List<SampleResult> results = new ArrayList<>();
        int missing = 0;
        Boolean placeholder = null;
        for (GroundTruthRow row : rows) {
            Path imagePath = imageDir.resolve(row.filename());
            if (!Files.isRegularFile(imagePath)) {
                missing++;
                continue;
            }
            PhBandClassifier.Code trueBand = PhBandClassifier
                    .classify(row.truePh(), row.truePh(), row.truePh(), 1.0, bands, false)
                    .code();
            try {
                byte[] bytes = Files.readAllBytes(imagePath);
                long start = System.nanoTime();
                PipelineOutcome outcome = wiring.pipeline().analyze(
                        bytes, RawImagePort.RoiRect.fullFrame(), VisionEngine.QuadHint.empty());
                long elapsedNanos = System.nanoTime() - start;
                if (placeholder == null) {
                    placeholder = outcome.chartIsPlaceholder();
                }
                Double predictedPh = outcome.phValue() == null ? null : outcome.phValue().doubleValue();
                PhBandClassifier.Code predictedBand = PhBandClassifier.Code.valueOf(
                        outcome.classification().name());
                String flags = outcome.qualityFlags().stream()
                        .map(f -> f.code().name())
                        .reduce((a, b) -> a + ";" + b)
                        .orElse("");
                results.add(new SampleResult(row.filename(), row.truePh(), trueBand, predictedPh,
                        predictedBand, outcome.inconclusive(), outcome.confidence().doubleValue(),
                        elapsedNanos, flags, null));
            } catch (IOException e) {
                results.add(new SampleResult(row.filename(), row.truePh(), trueBand, null, null,
                        true, 0.0, 0L, "", "IOException: " + e.getMessage()));
            } catch (RuntimeException e) {
                results.add(new SampleResult(row.filename(), row.truePh(), trueBand, null, null,
                        true, 0.0, 0L, "", e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }

        return summarize(rows.size(), missing, placeholder != null && placeholder, results);
    }

    private static Report summarize(int csvRows, int missing, boolean placeholder, List<SampleResult> results) {
        int failures = (int) results.stream().filter(SampleResult::failed).count();
        List<SampleResult> usable = results.stream().filter(r -> !r.failed()).toList();
        int inconclusive = (int) usable.stream().filter(SampleResult::inconclusive).count();
        List<SampleResult> comparable = usable.stream()
                .filter(r -> !r.inconclusive() && r.predictedPh() != null)
                .toList();

        Double mae = null;
        Double within025 = null;
        Double within05 = null;
        if (!comparable.isEmpty()) {
            double sumAbsErr = 0.0;
            long within025Count = 0;
            long within05Count = 0;
            for (SampleResult r : comparable) {
                double err = Math.abs(r.predictedPh() - r.truePh());
                sumAbsErr += err;
                if (err <= 0.25) {
                    within025Count++;
                }
                if (err <= 0.5) {
                    within05Count++;
                }
            }
            mae = sumAbsErr / comparable.size();
            within025 = 100.0 * within025Count / usable.size();
            within05 = 100.0 * within05Count / usable.size();
        }

        Long p50 = null;
        Long p95 = null;
        if (!usable.isEmpty()) {
            long[] sortedNanos = usable.stream().mapToLong(SampleResult::processingNanos).sorted().toArray();
            p50 = sortedNanos[percentileIndex(sortedNanos.length, 0.50)] / 1000;
            p95 = sortedNanos[percentileIndex(sortedNanos.length, 0.95)] / 1000;
        }

        Map<PhBandClassifier.Code, Map<PhBandClassifier.Code, Integer>> confusion = new TreeMap<>();
        for (SampleResult r : usable) {
            confusion.computeIfAbsent(r.trueBand(), k -> new TreeMap<>())
                    .merge(r.predictedBand(), 1, Integer::sum);
        }

        return new Report(csvRows, results.size() + missing, missing, failures, usable.size(),
                inconclusive, mae, within025, within05, p50, p95, placeholder, confusion, results, false);
    }

    private static int percentileIndex(int size, double percentile) {
        int idx = (int) Math.ceil(percentile * size) - 1;
        return Math.max(0, Math.min(size - 1, idx));
    }


    static String renderMarkdown(Report r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Bao cao harness do sai so pipeline quet mau cat (GD4)\n\n");
        sb.append("**CANH BAO**: neu du lieu dau vao la anh TONG HOP (sinh de khop bang mau), day la ")
                .append("self-consistency check (kiem wiring), KHONG PHAI so do do chinh xac quang hoc ")
                .append("thuc te. KHONG dung so nay de danh gia G1-G3 cua ke hoach production.\n\n");

        if (r.noData()) {
            sb.append("0 anh, chua co du lieu that.\n");
            return sb.toString();
        }

        sb.append("- So dong CSV: ").append(r.csvRows()).append('\n');
        sb.append("- Anh thieu file: ").append(r.missingImages()).append('\n');
        sb.append("- Loi xu ly (khong doc duoc anh / pipeline nem loi nghiep vu): ")
                .append(r.failures()).append('\n');
        sb.append("- Da chay pipeline thanh cong: ").append(r.evaluated()).append('\n');
        sb.append("- Bang mau la placeholder (chua hieu chuan thuc te): ")
                .append(r.chartIsPlaceholder()).append('\n');
        sb.append("- Ti le INCONCLUSIVE: ")
                .append(r.evaluated() == 0 ? "n/a" : pct(r.inconclusiveCount(), r.evaluated())).append('\n');
        sb.append("- MAE (chi tren mau KHONG inconclusive): ")
                .append(r.mae() == null ? "n/a" : "%.3f".formatted(r.mae())).append(" pH\n");
        sb.append("- %% trong +/-0.25 pH (tren toan bo mau da chay pipeline): ")
                .append(r.within025Pct() == null ? "n/a" : "%.1f%%".formatted(r.within025Pct())).append('\n');
        sb.append("- %% trong +/-0.5 pH (tren toan bo mau da chay pipeline): ")
                .append(r.within05Pct() == null ? "n/a" : "%.1f%%".formatted(r.within05Pct())).append('\n');
        sb.append("- Thoi gian xu ly p50/p95 (ms): ")
                .append(r.p50Micros() == null ? "n/a" : "%.1f".formatted(r.p50Micros() / 1000.0))
                .append(" / ")
                .append(r.p95Micros() == null ? "n/a" : "%.1f".formatted(r.p95Micros() / 1000.0))
                .append('\n');

        sb.append("\n## Ma tran nham lan theo dai (hang = true band, cot = predicted band)\n\n");
        List<PhBandClassifier.Code> codes = List.of(PhBandClassifier.Code.values());
        sb.append("| true \\\\ predicted |");
        for (PhBandClassifier.Code c : codes) {
            sb.append(' ').append(c).append(" |");
        }
        sb.append('\n');
        sb.append("|---|").append("---|".repeat(codes.size())).append('\n');
        for (PhBandClassifier.Code trueCode : codes) {
            Map<PhBandClassifier.Code, Integer> row = r.confusion().getOrDefault(trueCode, Map.of());
            if (row.isEmpty()) {
                continue;
            }
            sb.append("| ").append(trueCode).append(" |");
            for (PhBandClassifier.Code predCode : codes) {
                sb.append(' ').append(row.getOrDefault(predCode, 0)).append(" |");
            }
            sb.append('\n');
        }

        sb.append("\n## Chi tiet tung anh\n\n");
        sb.append("| filename | true_ph | predicted_ph | true_band | predicted_band | confidence | "
                + "quality_flags | loi |\n");
        sb.append("|---|---|---|---|---|---|---|---|\n");
        for (SampleResult s : r.samples()) {
            sb.append("| ").append(s.filename())
                    .append(" | ").append(s.truePh())
                    .append(" | ").append(s.predictedPh() == null ? "-" : s.predictedPh())
                    .append(" | ").append(s.trueBand())
                    .append(" | ").append(s.predictedBand() == null ? "-" : s.predictedBand())
                    .append(" | ").append("%.2f".formatted(s.confidence()))
                    .append(" | ").append(s.qualityFlags() == null || s.qualityFlags().isEmpty()
                            ? "-" : s.qualityFlags())
                    .append(" | ").append(s.error() == null ? "" : s.error())
                    .append(" |\n");
        }
        return sb.toString();
    }

    private static String pct(int count, int total) {
        return "%.1f%%".formatted(100.0 * count / total);
    }


    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: PipelineAccuracyHarness <thuMucAnh> <groundTruth.csv> [baoCao.md]");
            System.out.println("Vi du voi du lieu tong hop: xem SyntheticSandDatasetGenerator.main");
            return;
        }
        Path imageDir = Path.of(args[0]);
        Path csvPath = Path.of(args[1]);
        Report report = evaluate(imageDir, csvPath, System.out);
        String rendered = renderMarkdown(report);
        System.out.println(rendered);
        if (args.length >= 3) {
            Files.writeString(Path.of(args[2]), rendered);
        }
    }
}
