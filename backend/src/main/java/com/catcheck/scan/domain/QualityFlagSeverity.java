package com.catcheck.scan.domain;

/** Mức độ nghiêm trọng của một cờ chất lượng (p6 §6.3.4, p4 D3 {@code quality_flags}). */
public enum QualityFlagSeverity {
    /** Cảnh báo, vẫn cho ra kết quả (kèm dấu hiệu tham khảo thấp hơn). */
    WARN,
    /** Chặn — không thể kết luận, kết quả là {@code INCONCLUSIVE}. */
    BLOCKING
}
