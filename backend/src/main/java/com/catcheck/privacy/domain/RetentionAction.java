package com.catcheck.privacy.domain;

/**
 * Hành động khi dữ liệu hết thời hạn lưu (p4 §4.4.3 nhóm B, p15 §15.5).
 */
public enum RetentionAction {
    /** Xoá thật. */
    HARD_DELETE,
    /** Khử nhận dạng, giữ giá trị thống kê. */
    ANONYMIZE,
    /** Chuyển lưu trữ lạnh. */
    ARCHIVE,
    /** Che một phần (ví dụ octet cuối IPv4). */
    MASK
}
