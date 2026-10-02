package com.catcheck.privacy.domain;

/**
 * Căn cứ pháp lý để xử lý dữ liệu (p4 §4.4.3 nhóm B, p15 §15.2.2).
 */
public enum LegalBasis {
    /** Sự đồng ý của chủ thể dữ liệu. */
    CONSENT,
    /** Thực hiện thoả thuận (Đ19.1.d Luật BVDLCN). */
    CONTRACT,
    /** Nghĩa vụ luật định. */
    LEGAL_OBLIGATION,
    /** Lợi ích sống còn. */
    VITAL_INTEREST
}
