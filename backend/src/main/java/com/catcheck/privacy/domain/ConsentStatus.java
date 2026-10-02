package com.catcheck.privacy.domain;

/**
 * Trạng thái của một {@code consent_record} (p4 §4.4.3 nhóm B).
 *
 * <p>Mô hình rút đồng ý: thu hồi = <b>INSERT dòng mới</b> {@code WITHDRAWN}, không bao giờ
 * UPDATE dòng cũ — bất biến I16 (p4 §4.5.1), ép ở tầng DB bằng REVOKE UPDATE, DELETE.</p>
 */
public enum ConsentStatus {
    /** Đã đồng ý với mục đích này. */
    GRANTED,
    /** Từ chối ngay lúc được hỏi (ví dụ không tick checkbox tuỳ chọn lúc đăng ký). */
    DENIED,
    /** Rút lại sau khi đã đồng ý. */
    WITHDRAWN,
    /**
     * Chưa từng được hỏi — chỉ dùng ở tầng trình bày (view trạng thái hiện hành),
     * <b>không bao giờ</b> ghi vào {@code consent_record} (CHECK của V6 chỉ cho 3 giá
     * trị trên). Tồn tại vì "chưa đồng ý" là một trạng thái nghiệp vụ thật (p4 §4.4.1),
     * và "im lặng không phải là đồng ý" (p15 §15.3.1 C4).
     */
    NONE
}
