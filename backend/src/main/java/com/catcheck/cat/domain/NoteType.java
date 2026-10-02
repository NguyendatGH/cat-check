package com.catcheck.cat.domain;

/**
 * Loại ghi chú của chủ nuôi (p4 §4.4.4).
 *
 * <p>`SYMPTOM` là quan sát bất thường, KHÔNG phải chẩn đoán: ghi chú chỉ để lọc và đưa vào PDF cho bác
 * sĩ đọc, không dùng để suy luận bệnh (p4 C4). *
 * <p>Lưu dạng {@code VARCHAR + CHECK} (p4 §4.1.3): Hibernate ghi bằng {@code STRING}, không
 * dùng {@code @Enumerated(ORDINAL)} — đổi thứ tự hằng số sau này sẽ hỏng dữ liệu đã lưu.</p>
 */
public enum NoteType {

    GENERAL,
    DIET_CHANGE,
    SYMPTOM,
    VET_VISIT,
    LITTER_CHANGE
}
