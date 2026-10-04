package com.catcheck.scan.application;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.QualityFlagCode;
import com.catcheck.scan.domain.QualityFlagSeverity;
import com.catcheck.scan.domain.ScanClassification;
import com.catcheck.scan.domain.ScanThresholds;
import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.color.ConfidenceCalculator;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dựng {@link PipelineOutcome} từ một giá trị pH NHẬP TAY — không có ảnh, không chạy OpenCV.
 *
 * <p><b>Vì sao tồn tại:</b> {@link PipelineOutcome} là điểm cắt duy nhất giữa phần ảnh và phần
 * nghiệp vụ ({@link ScanPersistenceService} chỉ đọc record này). Mọi tính năng hạ nguồn — lịch
 * sử, xu hướng, export PDF, nhắc nhở, health flag, trừ credit FEFO — vì vậy chạy được THẬT mà
 * không cần pipeline ảnh. Lớp này KHÔNG thay thế {@link ColorPipelineService}: nó chỉ dùng cho
 * đường nhập thủ công dev-only ({@code POST /api/v1/dev/scans}, mặc định TẮT).
 *
 * <p><b>Những gì được suy ra thật, không bịa:</b> {@code classification} và
 * {@code nearBoundary} đi qua đúng {@link PhBandClassifier} + bảng {@code ph_classification_band}
 * đọc từ {@link ChartCatalog} (p6 §6.7.1/§6.7.2), nên pH nhập tay cho ra cùng mã phân loại như
 * pH do pipeline đo. {@code chartId}/{@code chartVersion}/{@code chartIsPlaceholder} cũng lấy từ
 * bảng màu đang ACTIVE — giữ nguyên hiệu lực của luật "R1–R3 tắt khi bảng màu còn placeholder"
 * (p6 §6.9.1).
 *
 * <p><b>Những gì cố ý để trống:</b> mọi trường đo màu (Lab, ΔE, blob, match) là {@code null} vì
 * không có ảnh để đo — điền số giả vào đó sẽ làm màn "phân tích chi tiết" hiển thị dữ liệu bịa.
 * {@code calibrationMethod = NONE} vì đúng là không có bước hiệu chỉnh nào chạy, và
 * {@code engineVersion = }{@value #ENGINE_VERSION} để mọi bản ghi nhập tay phân biệt được với
 * bản ghi do pipeline thật sinh ra (truy vấn một câu là lọc sạch dữ liệu demo).
 */
@Service
public class ManualPipelineOutcomeFactory {

    /**
     * Khác hẳn {@link PipelineOutcome#ENGINE_VERSION} ({@code "1.0.0"}) có chủ đích: hàng nhập
     * tay phải nhận ra được bằng {@code scan_analysis.engine_version}.
     */
    public static final String ENGINE_VERSION = "manual-1.0.0";

    /**
     * Confidence mặc định khi người nhập không truyền: 0.80 nằm trong dải {@code HIGH}
     * (≥ 0,75, {@link ConfidenceCalculator}) — nhập tay nghĩa là con người đã đọc giá trị pH,
     * nên không có lý do để giả vờ kém tin cậy. Vẫn có thể hạ xuống qua tham số để demo
     * R4 {@code LOW_QUALITY_STREAK} (p6 §6.9.5).
     */
    public static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.800");

    /**
     * Confidence mặc định cho bản ghi {@code INCONCLUSIVE} — chép đúng con số
     * {@link ColorPipelineService} ghi khi quality gate chặn trước khi S8/S9 kịp chạy. Không
     * dùng {@link #DEFAULT_CONFIDENCE}: một hàng "chưa đủ dữ liệu để kết luận" mà confidence
     * 0,80 là mâu thuẫn tự thân, và rule R4 {@code LOW_QUALITY_STREAK} đọc đúng cột này.
     */
    public static final BigDecimal INCONCLUSIVE_CONFIDENCE = new BigDecimal("0.200");

    /**
     * Nửa độ rộng khoảng tin cậy quanh pH nhập tay. 0,1 là MAE mục tiêu của pipeline (p6
     * research J14 ghi 0,15) làm tròn về bước 0,1 của {@code NUMERIC(3,1)} — không lấy 0 vì
     * {@code phLow = phHigh} sẽ làm cờ {@code NEAR_BOUNDARY} không bao giờ bật được.
     */
    private static final BigDecimal CI_HALF_WIDTH = new BigDecimal("0.1");

    private final ChartCatalog chartCatalog;

    public ManualPipelineOutcomeFactory(ChartCatalog chartCatalog) {
        this.chartCatalog = chartCatalog;
    }

    /**
     * @param phValue              pH nhập tay; {@code null} ⇒ {@code INCONCLUSIVE}
     * @param forcedClassification ép phân loại (chỉ dùng để dựng dữ liệu demo cho một nhánh UI
     *                             cụ thể); {@code null} ⇒ suy ra bằng {@link PhBandClassifier}
     * @param requestedConfidence  {@code null} ⇒ {@link #DEFAULT_CONFIDENCE}
     * @param qualityFlags         cờ chất lượng muốn gắn; {@code null}/rỗng ⇒ không cờ nào.
     *                             Có cờ {@code BLOCKING} ⇒ {@code INCONCLUSIVE}, đúng như
     *                             {@link PhBandClassifier#classify} xử lý ảnh thật
     */
    public PipelineOutcome fromManualPh(BigDecimal phValue,
                                        ScanClassification forcedClassification,
                                        BigDecimal requestedConfidence,
                                        List<QualityFlag> qualityFlags) {

        List<QualityFlag> flags = qualityFlags == null ? List.of() : List.copyOf(qualityFlags);
        BigDecimal confidence = (requestedConfidence == null ? DEFAULT_CONFIDENCE : requestedConfidence)
                .setScale(3, RoundingMode.HALF_UP);
        boolean blocking = flags.stream().anyMatch(f -> f.severity() == QualityFlagSeverity.BLOCKING);

        Optional<PhChart> chart = chartCatalog.findActiveChart(ScanThresholds.DEFAULT_PRODUCT_LINE, null);

        BigDecimal ph = phValue == null ? null : phValue.setScale(1, RoundingMode.HALF_UP);
        BigDecimal phLow = ph == null ? null : ph.subtract(CI_HALF_WIDTH);
        BigDecimal phHigh = ph == null ? null : ph.add(CI_HALF_WIDTH);

        ScanClassification classification;
        boolean nearBoundary;
        if (ph == null) {
            classification = ScanClassification.INCONCLUSIVE;
            nearBoundary = false;
        } else {
            PhBandClassifier.Result classified = PhBandClassifier.classify(
                    ph.doubleValue(), phLow.doubleValue(), phHigh.doubleValue(),
                    confidence.doubleValue(), chartCatalog.findGlobalBands(), blocking);
            classification = ScanClassification.valueOf(classified.code().name());
            nearBoundary = classified.nearBoundary();
        }
        if (forcedClassification != null) {
            classification = forcedClassification;
        }

        boolean inconclusive = classification == ScanClassification.INCONCLUSIVE;
        if (inconclusive) {
            // Giống nhánh inconclusive của ColorPipelineService: không có kết luận thì không
            // có pH để hiển thị, và "sát ranh giới" là vô nghĩa.
            ph = null;
            phLow = null;
            phHigh = null;
            nearBoundary = false;
            if (requestedConfidence == null) {
                confidence = INCONCLUSIVE_CONFIDENCE;
            }
        }

        return new PipelineOutcome(
                inconclusive,
                classification,
                ph, phLow, phHigh,
                confidence,
                confidenceBandOf(confidence),
                nearBoundary,
                // Lab / ΔE / blob / match: không có ảnh nên không có số đo nào.
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null,
                CalibrationMethod.NONE,
                null,
                Map.of("method", CalibrationMethod.NONE.name(), "source", "MANUAL"),
                Map.of(),
                flags,
                chart.map(c -> parseUuidOrNull(c.id())).orElse(null),
                chart.map(PhChart::version).orElse(null),
                chart.map(PhChart::isPlaceholder).orElse(false),
                ENGINE_VERSION,
                0,
                null,
                0, 0, null);
    }

    /**
     * Mức độ mặc định của một mã cờ chất lượng — chép đúng cách {@link ColorPipelineService}
     * gắn {@link QualityFlagSeverity} cho từng mã khi chạy ảnh thật, để dữ liệu nhập tay không
     * sinh ra tổ hợp (mã, mức độ) mà pipeline thật không bao giờ tạo được.
     *
     * <p>{@code CALIBRATION_UNAVAILABLE} là {@code WARN} chứ không {@code BLOCKING}: javadoc
     * của {@code QualityFlagCode} ghi BLOCKING nhưng {@link ColorPipelineService} gắn
     * {@code WARN} — bản chạy thật là bản quyết định.
     */
    public static QualityFlag flagOf(QualityFlagCode code) {
        QualityFlagSeverity severity = switch (code) {
            case BLURRY, TOO_DARK, TOO_BRIGHT, OVEREXPOSED, NO_INDICATOR_GRAINS -> QualityFlagSeverity.BLOCKING;
            case UNDEREXPOSED_PATCH, UNEVEN_LIGHTING, CARD_NOT_FOUND, CALIBRATION_UNAVAILABLE,
                 LOW_GRANULE_COVERAGE, FEW_BLOBS, MIXED_COLORS, OUT_OF_CHART_RANGE -> QualityFlagSeverity.WARN;
        };
        return new QualityFlag(code, severity);
    }

    /**
     * Cùng ngưỡng với {@code ConfidenceCalculator.bandOf} (private ở lớp đó). Không gọi lại
     * được nên chép ngưỡng kèm neo hằng số công khai {@link ConfidenceCalculator#MIN_RESULT_CONFIDENCE}
     * để hai nơi không trôi khỏi nhau trong im lặng.
     */
    private String confidenceBandOf(BigDecimal confidence) {
        double value = confidence.doubleValue();
        if (value >= 0.75) {
            return "HIGH";
        }
        return value >= ConfidenceCalculator.MIN_RESULT_CONFIDENCE ? "MEDIUM" : "LOW";
    }

    private UUID parseUuidOrNull(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
