package com.catcheck.scan.domain;

/**
 * Hằng số ngưỡng chất lượng &amp; tham số ảnh của pipeline — nguồn sự thật duy nhất, dùng cả ở
 * {@code scan.application.ColorPipelineService} (kiểm tra) lẫn ở
 * {@code GET /api/v1/scan/config} (công bố cho client, p6 §6.3.4/§6.3.5, p8 E12).
 *
 * <p>Là hằng số Java thay vì {@code app_setting}/{@code @ConfigurationProperties}: p6 §6.3.4 ghi
 * rõ "giá trị khởi tạo, không phải chân lý... phải hiệu chỉnh lại trên bộ ảnh thật trước
 * go-live" (M7, ngoài phạm vi MVP) — chưa có bộ ảnh thật nên chưa có gì để cấu hình động, và
 * việc thêm một cơ chế cấu hình runtime cho các số sẽ đổi ở M7 là over-engineering ở M3.</p>
 */
public final class ScanThresholds {

    private ScanThresholds() {
    }

    // ---- Kích thước & định dạng ảnh (p6 §6.3.5, §6.4.1) ----
    public static final int MIN_EDGE_PX = 640;
    public static final int MAX_EDGE_PX = 4096;
    public static final int WORKING_EDGE_PX = 1280;
    public static final long MAX_UPLOAD_BYTES = 8L * 1024 * 1024;
    public static final double JPEG_QUALITY = 0.92;
    public static final int CROP_MARGIN_PCT = 8;

    // ---- Ngưỡng chất lượng Q1-Q5 (p6 §6.3.4), siết thêm ở S2 khi precheckAvailable=false ----
    public static final double BLUR_VAR_MIN = 80.0;
    public static final double MEAN_LUMA_MIN = 60.0;
    public static final double MEAN_LUMA_MAX = 210.0;
    public static final double CLIP_HIGH_MAX = 0.02;
    public static final double CLIP_LOW_MAX = 0.05;
    public static final double LUMA_GRADIENT_MAX = 60.0;
    public static final double TILT_DEG_MAX = 25.0;

    // ---- S4b — fallback không thẻ (p6 §6.5.1 S4b) ----
    public static final double SUBSTRATE_CHROMA_MAX_NEUTRAL = 6.0;
    public static final double SUBSTRATE_L_MIN = 55.0;
    public static final double SUBSTRATE_L_MAX = 92.0;
    public static final double MIN_NEUTRAL_RATIO = 0.15;

    // ---- S6 — tách hạt chỉ thị (dòng STANDARD, p6 §6.5.1 S6) ----
    public static final double MIN_COVERAGE_STANDARD = 0.015;
    public static final double HARD_MIN_COVERAGE = 0.005;

    // ---- S9 — ngưỡng kết luận (khớp com.catcheck.scan.domain.color.ConfidenceCalculator) ----
    public static final double MIN_RESULT_CONFIDENCE = 0.40;

    // ---- Rate limit & timeout (p6 §6.4.1) ----
    public static final int MAX_SCANS_PER_MINUTE = 10;
    public static final int MAX_SCANS_PER_HOUR = 60;

    // ---- Sửa mèo sau khi lưu (p6 §6.10.3, p8 E9) ----
    public static final int REASSIGN_WINDOW_HOURS = 24;
    public static final int REASSIGN_MAX_COUNT = 3;

    // ---- Bảng màu mặc định MVP — chỉ 1 dòng sản phẩm có bảng màu (V9 seed, p4 D5) ----
    public static final String DEFAULT_PRODUCT_LINE = "STANDARD";

    /** Định dạng ảnh chấp nhận (magic-byte sniff, p6 §6.4.1). */
    public static final String[] ACCEPTED_MIME_TYPES = {"image/jpeg", "image/png", "image/webp"};
}
