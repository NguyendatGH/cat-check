package com.catcheck.scan.tooling;

import com.catcheck.scan.domain.color.ColorSpace;
import com.catcheck.scan.domain.color.PhChart;
import com.catcheck.scan.domain.color.PhChartPoint;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class SyntheticSandDatasetGenerator {

    private static final int CANVAS_SIZE = 700;
    private static final int GRAIN_DIAMETER = 14;
    private static final int GRAINS_PER_IMAGE = 220;
    // Nền phải là màu cát nền: pipeline cân bằng trắng theo nó, nền xám làm lệch màu hạt và trượt dải pH.
    private static final Color BACKGROUND =
            Color.decode(ColorSpace.labToHex(PipelineAccuracyHarness.REFERENCE_SUBSTRATE_LAB));
    private static final int NOISE_AMPLITUDE = 8;
    private static final int GRAIN_NOISE_AMPLITUDE = 5;

    private SyntheticSandDatasetGenerator() {
    }

    static Path generate(Path outputDir, int imagesPerPoint, long seed) throws IOException {
        Files.createDirectories(outputDir);
        LocalDbChartCatalog catalog = LocalDbChartCatalog.fromSystemPropertiesOrLocalDefaults();
        PhChart chart = catalog.findActiveChart("STANDARD", null)
                .orElseThrow(() -> new IllegalStateException(
                        "Khong tim thay bang mau ACTIVE (STANDARD) trong DB local - chay migration/seed "
                                + "truoc (xem backend/README.md)"));

        Random rng = new Random(seed);
        String[] phones = {"pixel-7", "iphone-13", "generic-android"};
        String[] lightings = {"daylight", "white-led", "warm-led"};

        List<String> csvLines = new ArrayList<>();
        csvLines.add("filename,true_ph,phone,lighting,minutes_after_wet,has_card");
        int counter = 0;
        for (PhChartPoint point : chart.points()) {
            for (int i = 0; i < imagesPerPoint; i++) {
                String filename = "synthetic-ph%s-%d.png".formatted(
                        String.valueOf(point.phValue()).replace('.', '_'), i);
                BufferedImage image = renderImage(point.swatchHex(), rng);
                ImageIO.write(image, "png", outputDir.resolve(filename).toFile());

                String phone = phones[counter % phones.length];
                String lighting = lightings[counter % lightings.length];
                int minutesAfterWet = 10 + (counter % 5) * 10;
                csvLines.add(String.join(",", filename, String.valueOf(point.phValue()), phone,
                        lighting, String.valueOf(minutesAfterWet), "false"));
                counter++;
            }
        }
        Path csvPath = outputDir.resolve("ground_truth.csv");
        Files.write(csvPath, csvLines);
        return csvPath;
    }

    private static BufferedImage renderImage(String grainHex, Random rng) {
        BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < CANVAS_SIZE; y++) {
            for (int x = 0; x < CANVAS_SIZE; x++) {
                int r = clamp(BACKGROUND.getRed() + rng.nextInt(2 * NOISE_AMPLITUDE + 1) - NOISE_AMPLITUDE);
                int g = clamp(BACKGROUND.getGreen() + rng.nextInt(2 * NOISE_AMPLITUDE + 1) - NOISE_AMPLITUDE);
                int b = clamp(BACKGROUND.getBlue() + rng.nextInt(2 * NOISE_AMPLITUDE + 1) - NOISE_AMPLITUDE);
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        Graphics2D g = image.createGraphics();
        try {
            Color base = Color.decode(grainHex);
            for (int i = 0; i < GRAINS_PER_IMAGE; i++) {
                int cx = GRAIN_DIAMETER + rng.nextInt(CANVAS_SIZE - 2 * GRAIN_DIAMETER);
                int cy = GRAIN_DIAMETER + rng.nextInt(CANVAS_SIZE - 2 * GRAIN_DIAMETER);
                g.setColor(jitter(base, rng));
                g.fillOval(cx, cy, GRAIN_DIAMETER, GRAIN_DIAMETER);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static Color jitter(Color base, Random rng) {
        int noiseSpan = 2 * GRAIN_NOISE_AMPLITUDE + 1;
        int r = clamp(base.getRed() + rng.nextInt(noiseSpan) - GRAIN_NOISE_AMPLITUDE);
        int g = clamp(base.getGreen() + rng.nextInt(noiseSpan) - GRAIN_NOISE_AMPLITUDE);
        int b = clamp(base.getBlue() + rng.nextInt(noiseSpan) - GRAIN_NOISE_AMPLITUDE);
        return new Color(r, g, b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: SyntheticSandDatasetGenerator <outputDir> [imagesPerPoint=2] [seed=42]");
            return;
        }
        Path outputDir = Path.of(args[0]);
        int imagesPerPoint = args.length >= 2 ? Integer.parseInt(args[1]) : 2;
        long seed = args.length >= 3 ? Long.parseLong(args[2]) : 42L;
        Path csv = generate(outputDir, imagesPerPoint, seed);
        System.out.println("Da sinh du lieu tong hop vao " + outputDir.toAbsolutePath());
        System.out.println("CSV ground truth: " + csv.toAbsolutePath());
    }
}
