package com.catcheck.cat.domain;

import java.util.Arrays;

/**
 * Câu 4 của bộ khảo sát 5 câu (C29, p4 §4.4.4).
 *
 * <p><b>Tên hằng số Java KHÔNG giống giá trị JSON.</b> p4 cố định giá trị trong
 * {@code cat_health_survey.answers} là {@code 1_2_PER_DAY}, nhưng {@code 1_2_PER_DAY} không phải
 * định danh Java hợp lệ (ký tự gạch dưới không được ở giữa tên). Nên hằng số đặt là
 * {@link #ONE_TO_TWO_PER_DAY} và {@link #wireValue()} trả về đúng chuỗi p4 yêu cầu.</p>
 *
 * <p>Đây là enum để <i>diễn giải</i> câu trả lời trong JSONB, không phải để ghi vào một cột
 * {@code VARCHAR + CHECK}: p4 §4.4.4 chỉ định validate {@code answers} bằng JSON Schema theo
 * {@code questionnaire_version}. Vì vậy hợp đồng dây là {@link #wireValue()}, và
 * {@link #fromWire(String)} là chiều ngược của nó.</p>
 */
public enum UrinationFrequency {

    LT_1_PER_DAY("LT_1_PER_DAY"),
    ONE_TO_TWO_PER_DAY("1_2_PER_DAY"),
    GT_2_PER_DAY("GT_2_PER_DAY"),
    UNKNOWN("UNKNOWN");

    private final String wireValue;

    UrinationFrequency(String wireValue) {
        this.wireValue = wireValue;
    }

    /** Đúng chuỗi p4 dùng trong JSONB, ví dụ {@code "1_2_PER_DAY"}. */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Chiều ngược của {@link #wireValue()}.
     *
     * @throws IllegalArgumentException nếu chuỗi không thuộc tập hằng số — bắt buộc, vì giá trị đến
     *                                  từ JSONB do người dùng gửi và có thể là bất cứ thứ gì
     */
    public static UrinationFrequency fromWire(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("urinationFrequency không hợp lệ: " + value));
    }
}
