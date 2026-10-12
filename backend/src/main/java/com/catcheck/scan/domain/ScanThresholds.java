package com.catcheck.scan.domain;

public final class ScanThresholds {

    private ScanThresholds() {
    }

    public static final int MIN_EDGE_PX = 640;
    public static final int MAX_EDGE_PX = 4096;
    public static final int WORKING_EDGE_PX = 1280;
    public static final long MAX_UPLOAD_BYTES = 8L * 1024 * 1024;
    public static final double JPEG_QUALITY = 0.92;
    public static final int CROP_MARGIN_PCT = 8;

    public static final double BLUR_VAR_MIN = 80.0;
    public static final double MEAN_LUMA_MIN = 60.0;
    public static final double MEAN_LUMA_MAX = 210.0;
    public static final double CLIP_HIGH_MAX = 0.02;
    public static final double CLIP_LOW_MAX = 0.05;
    public static final double LUMA_GRADIENT_MAX = 60.0;
    public static final double TILT_DEG_MAX = 25.0;

    public static final double SUBSTRATE_CHROMA_MAX_NEUTRAL = 18.0;
    public static final double SUBSTRATE_L_MIN = 55.0;
    public static final double SUBSTRATE_L_MAX = 92.0;
    public static final double MIN_NEUTRAL_RATIO = 0.15;

    public static final double MIN_COVERAGE_STANDARD = 0.015;
    public static final double HARD_MIN_COVERAGE = 0.005;

    public static final double MIN_RESULT_CONFIDENCE = 0.40;

    public static final int MAX_SCANS_PER_MINUTE = 10;
    public static final int MAX_SCANS_PER_HOUR = 60;

    public static final int REASSIGN_WINDOW_HOURS = 24;
    public static final int REASSIGN_MAX_COUNT = 3;

    public static final String DEFAULT_PRODUCT_LINE = "STANDARD";

    public static final String DEFAULT_CARD_LAYOUT_CODE = "CC-CARD";
    public static final double CARD_CCM_MAX_RESIDUAL_DE00 = 4.0;
    public static final double CARD_CCM_MIN_ABS_DETERMINANT = 0.05;

    public static final String[] ACCEPTED_MIME_TYPES = {"image/jpeg", "image/png", "image/webp"};
}
