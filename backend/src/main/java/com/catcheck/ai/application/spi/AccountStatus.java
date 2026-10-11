package com.catcheck.ai.application.spi;

/**
 * Sáu trạng thái vòng đời tài khoản, sao chép từ {@code app_user.status} (p4 §4.4.2).
 *
 * <p><b>Sao chép, không dùng chung kiểu</b> — cùng kỹ thuật
 * {@code community.application.spi.AccountStatus}/{@code cat.application.spi.AccountStatus}:
 * {@code ai/package-info.java} không cho phụ thuộc {@code identity}, nên module này khai bản sao
 * chỉ đủ dùng và adapter ở {@code ai.infrastructure.persistence} đọc thẳng {@code app_user.status}
 * bằng JDBC thô.</p>
 */
public enum AccountStatus {

    PENDING_VERIFICATION,
    ACTIVE,
    LOCKED,
    /** Người dùng yêu cầu hạn chế xử lý: CHỈ ĐỌC (p15 §15.4.7). */
    RESTRICTED,
    DELETION_REQUESTED,
    ANONYMIZED;

    /**
     * @throws IllegalArgumentException nếu DB có giá trị ngoài sáu giá trị trên (schema lệch mã
     *                                  nguồn — không nuốt, xem javadoc bản của community)
     */
    public static AccountStatus fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("account status không được null");
        }
        for (AccountStatus candidate : values()) {
            if (candidate.name().equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("account status không hợp lệ: " + value);
    }

    /**
     * I19 (p4 §4.4.9): tài khoản {@code RESTRICTED} không được xử lý gì ngoài lưu trữ — với trợ lý
     * AI nghĩa là không lưu tin nhắn mới và không gửi câu hỏi ra nhà cung cấp LLM
     * ({@code docs/cat-rag-credit-pipeline.md} A4-(5)).
     */
    public boolean allowsWrite() {
        return this == ACTIVE;
    }
}
