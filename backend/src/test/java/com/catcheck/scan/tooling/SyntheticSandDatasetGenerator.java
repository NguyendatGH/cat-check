package com.catcheck.scan.tooling;

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

/**
 * Sinh bộ ảnh TỔNG HỢP + CSV ground truth để chạy thử {@link PipelineAccuracyHarness} end-to-end
 * khi CHƯA có ảnh thật từ GĐ1 ({@code docs/scan-production-plan.md} mục §7(2)).
 *
 * <h2>Đây KHÔNG phải ảnh thật — self-consistency check, không đo độ chính xác quang học</h2>
 * <p>Mỗi ảnh là: nền xám trung tính có nhiễu nhẹ (qua được ngưỡng độ nét Q1 của
 * {@code ScanThresholds}) + các "hạt chỉ thị" hình tròn tô ĐÚNG màu {@code display_hex} của một
 * mốc pH lấy từ bảng màu ACTIVE hiện có trong DB local (qua {@link LocalDbChartCatalog}, ví dụ
 * {@code CHART-PLACEHOLDER-v1}). Ảnh được THIẾT KẾ ĐỂ KHỚP pipeline (không mô phỏng quang học máy
 * ảnh/ánh sáng thật) — dùng để xác nhận đường đi (wiring) chạy đúng đầu-cuối, KHÔNG dùng số liệu
 * từ bộ này để báo cáo G1–G3 của kế hoạch production.
 *
 * <p>Không tạo file nào trong repo: luôn truyền một đường dẫn ngoài {@code backend/} (ví dụ thư
 * mục scratchpad) làm {@code outputDir}.
 */
final class SyntheticSandDatasetGenerator {

    /** Lớn hơn hẳn {@code ScanThresholds.MIN_EDGE_PX} (640) để không dính biên. */
    private static final int CANVAS_SIZE = 700;
    private static final int GRAIN_DIAMETER = 14;
    // Lon hon han muc can de co "hat" (p6 S6): cv::kmeans k=3 (OpenCvVisionEngine) thuong chi
    // chon duoc MOT PHAN cac hat lam cum "chroma cao nhat" khi cac hat co nhieu mau nhe giua nhau
    // (xem Javadoc jitter()) - sinh nhieu hat de phan con lai van vuot han MIN_COVERAGE_STANDARD.
    private static final int GRAINS_PER_IMAGE = 220;
    private static final int BACKGROUND_GRAY = 185;
    private static final int NOISE_AMPLITUDE = 8;
    private static final int GRAIN_NOISE_AMPLITUDE = 5;

    private SyntheticSandDatasetGenerator() {
    }

    /**
     * Sinh {@code imagesPerPoint} ảnh cho MỖI mốc pH của bảng ACTIVE hiện tại + file
     * {@code ground_truth.csv} khớp schema của {@link PipelineAccuracyHarness} (Javadoc lớp đó).
     * KHÔNG ghi gì liên quan tới người chụp.
     *
     * @return đường dẫn tới {@code ground_truth.csv} đã viết
     */
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

    /** Vẽ một ảnh: nền cát nhiễu nhẹ trung tính + {@code GRAINS_PER_IMAGE} hạt màu {@code grainHex}. */
    private static BufferedImage renderImage(String grainHex, Random rng) {
        BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < CANVAS_SIZE; y++) {
            for (int x = 0; x < CANVAS_SIZE; x++) {
                // Nhiễu CÙNG mot gia tri tren ca 3 kenh -> nen luon trung tinh tuyet doi (chroma=0),
                // chi tao bien thien do net (Laplacian) de qua nguong Q1, khong lam lech mau nen.
                int v = clamp(BACKGROUND_GRAY + rng.nextInt(2 * NOISE_AMPLITUDE + 1) - NOISE_AMPLITUDE);
                image.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        Graphics2D g = image.createGraphics();
        try {
            Color base = Color.decode(grainHex);
            for (int i = 0; i < GRAINS_PER_IMAGE; i++) {
                int cx = GRAIN_DIAMETER + rng.nextInt(CANVAS_SIZE - 2 * GRAIN_DIAMETER);
                int cy = GRAIN_DIAMETER + rng.nextInt(CANVAS_SIZE - 2 * GRAIN_DIAMETER);
                // Nhieu MOT LAN cho CA hat (khong phai tung pixel) - xem Javadoc jitter().
                g.setColor(jitter(base, rng));
                g.fillOval(cx, cy, GRAIN_DIAMETER, GRAIN_DIAMETER);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Nhiễu nhỏ, CÙNG một lượng cho MỌI pixel của MỘT hạt (không phải nhiễu độc lập từng pixel).
     *
     * <p>Lý do: {@code cv::kmeans k=3} trong {@code OpenCvVisionEngine.selectIndicatorGrains} (S6)
     * cần các hạt có BIẾN THIÊN MÀU GIỮA CÁC HẠT để không suy biến — xác nhận THỰC TẾ (không chỉ lý
     * thuyết): với một màu đặc tuyệt đối giống hệt nhau cho mọi pixel hạt trong ảnh (không có bước
     * này), {@code KMEANS_PP_CENTERS} trên dữ liệu trùng lặp y hệt ra kết quả không ổn định — một số
     * mốc pH mất trắng {@code NO_INDICATOR_GRAINS} dù ΔE00/chroma tới nền đều cao hơn hẳn ngưỡng
     * (tính tay bằng {@code ColorSpace}/{@code DeltaE2000}, không phải do màu "khó" thật sự).
     *
     * <p>Nhiễu phải nhất quán TRONG một hạt: nhiễu độc lập từng pixel (đã thử, bỏ — xem lịch sử
     * commit) tạo nhiễu kiểu muối-tiêu phá vỡ {@code MORPH_OPEN} (3×3): mọi hạt biến mất vì không
     * còn cụm pixel liền kề đủ lớn, ra {@code NO_INDICATOR_GRAINS} cho CẢ 7 mốc thay vì chỉ 3. Nhiễu
     * theo TỪNG HẠT (một giá trị ngẫu nhiên áp cho toàn bộ hình tròn) vừa cho kmeans đủ cấu trúc để
     * tách cụm ổn định, vừa giữ mỗi hạt liền khối để sống sót qua morphology.
     */
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

    /** {@code args}: {@code <outputDir> [imagesPerPoint=2] [seed=42]}. */
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
