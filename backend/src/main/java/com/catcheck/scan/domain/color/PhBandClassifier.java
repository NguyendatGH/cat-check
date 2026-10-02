package com.catcheck.scan.domain.color;

import java.util.List;

/**
 * Bước <b>S10 — phân loại kết quả</b> theo bảng {@code ph_classification_band} (p6 §6.7).
 *
 * <h2>Ranh giới 6.3 và 6.6 là dữ liệu, không phải hằng số UI</h2>
 * <p>Code này <b>không</b> tự đặt nhãn hiển thị. Mọi nhãn đến từ {@link Band#labelKey()} đã seed trong
 * DB và được frontend đọc qua {@code GET /api/v1/reference/ph-bands}. Nếu hard-code nhãn ở đây thì
 * sẽ có hai nguồn sự thật và chúng chắc chắn lệch nhau sau lần sửa đầu tiên.
 *
 * <p>Ranh giới 6.3/6.6 cũng đọc từ bảng, không viết thẳng trong {@link #classify}: bảng phân loại
 * được owner chỉnh được, và nếu code cứng luật thì thay đổi ở DB sẽ im lặng không có tác dụng.
 */
public final class PhBandClassifier {

    private PhBandClassifier() {
        throw new AssertionError("PhBandClassifier la lop tien ich, khong instantiate");
    }

    /**
     * Một dải phân loại, ánh xạ {@code ph_classification_band} (p4 §4.9).
     *
     * @param code        mã kết quả
     * @param minPh       cận dưới, {@code null} nghĩa là −∞
     * @param maxPh       cận trên, {@code null} nghĩa là +∞
     * @param labelKey    khoá nhãn đã seed — <b>nhãn hiển thị lấy từ đây, không viết trong code</b>
     * @param severity    mức độ
     * @param colorToken  token màu vai trò {@code color-ph-*}
     * @param icon        tên icon
     * @param sortOrder   thứ tự hiển thị
     */
    public record Band(
            Code code,
            Double minPh,
            Double maxPh,
            boolean minInclusive,
            boolean maxInclusive,
            String labelKey,
            Severity severity,
            String colorToken,
            String icon,
            int sortOrder) {

        /**
         * Kiểm tra pH có thuộc dải không, tôn trọng {@code minInclusive}/{@code maxInclusive}.
         *
         * <p>Seed dùng ranh giới khép kín có chủ đich: 6.0 thuộc SLIGHTLY_LOW (không phải LOW),
         * 6.3 và 6.6 thuộc IN_RANGE, 7.0 thuộc SLIGHTLY_HIGH. Nếu không tôn trọng inclusivity
         * thì 6.0 sẽ khớp cả LOW lẫn SLIGHTLY_LOW và phải dùng sortOrder để phân xử — cách đó
         * vô tình đảo kết quả khi seed đổi thứ tự.
         */
        public boolean contains(double ph) {
            boolean aboveMin = minPh == null || (minInclusive ? ph >= minPh : ph > minPh);
            boolean belowMax = maxPh == null || (maxInclusive ? ph <= maxPh : ph < maxPh);
            return aboveMin && belowMax;
        }
    }

    /** Mã kết quả — trùng đúng tập trong bảng seed. */
    public enum Code {
        IN_RANGE, SLIGHTLY_LOW, SLIGHTLY_HIGH, LOW, HIGH, INCONCLUSIVE
    }

    public enum Severity {
        NORMAL, ATTENTION, WATCH, NEUTRAL
    }

    /** Kết quả phân loại đầy đủ. */
    public record Result(Band band, boolean nearBoundary) {

        public Code code() {
            return band.code();
        }
    }

    /**
     * Phân loại theo bảng band từ DB.
     *
     * @param ph         pH ước lượng
     * @param phLow      cận dưới CI
     * @param phHigh     cận trên CI
     * @param confidence confidence S9
     * @param bands      bảng {@code ph_classification_band}, đã sắp theo {@code sort_order}
     * @param blocking   có cờ BLOCKING hay không (ảnh lỗi chất lượng, thẻ hỏng…)
     */
    public static Result classify(double ph, double phLow, double phHigh, double confidence,
                                  List<Band> bands, boolean blocking) {
        if (bands == null || bands.isEmpty()) {
            throw new IllegalArgumentException("Bang phan loai rong — phai seed tu ph_classification_band");
        }
        if (confidence < ConfidenceCalculator.MIN_RESULT_CONFIDENCE || blocking) {
            return new Result(requireCode(bands, Code.INCONCLUSIVE), false);
        }
        Band matched = bands.stream()
                .filter(band -> band.contains(ph))
                .min((x, y) -> Integer.compare(x.sortOrder(), y.sortOrder()))
                .orElseThrow(() -> new IllegalStateException(
                        "Khong co dai nao chua pH " + ph + " — bang phan loai co lo hoac cho phep trong khoang"));
        return new Result(matched, nearBoundary(phLow, phHigh, bands));
    }

    /**
     * Cờ {@code NEAR_BOUNDARY} — bắt buộc (p6 §6.7.2).
     *
     * <p>Ý nghĩa: khoảng tin cậy có cắt qua một ranh giới phân loại nghĩa là chưa biết mèo nằm bên
     * nào. Bỏ cờ này, người dùng thấy 6,61 hôm nay và 6,59 ngày mai rồi tưởng có biến động thật.
     *
     * <p>Ranh giới lấy từ dải {@code IN_RANGE} trong DB (mặc định 6.3 và 6.6) chứ không viết thẳng
     * trong code, để việc owner chỉnh ngưỡng không cần sửa Java.
     *
     * <p>Với {@code INCONCLUSIVE} cờ này luôn {@code false}: chưa có kết luận thì "sát ranh giới"
     * là vô nghĩa.
     */
    public static boolean nearBoundary(double phLow, double phHigh, List<Band> bands) {
        Band inRange = requireCode(bands, Code.IN_RANGE);
        if (inRange.minPh() == null || inRange.maxPh() == null) {
            return false;
        }
        return straddles(phLow, phHigh, inRange.minPh()) || straddles(phLow, phHigh, inRange.maxPh());
    }

    /** CI có cắt qua mốc {@code boundary} hay không (dùng < và > để mốc nằm đúng ranh không bị coi là sát). */
    private static boolean straddles(double phLow, double phHigh, double boundary) {
        return phLow < boundary && phHigh > boundary;
    }

    private static Band requireCode(List<Band> bands, Code code) {
        return bands.stream()
                .filter(band -> band.code() == code)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Bang phan loai thieu ma " + code));
    }
}
