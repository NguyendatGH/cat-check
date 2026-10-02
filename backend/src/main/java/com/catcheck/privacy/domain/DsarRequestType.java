package com.catcheck.privacy.domain;

/**
 * Loại yêu cầu quyền của chủ thể dữ liệu (p4 §4.4.3 nhóm B, p15 §15.4).
 *
 * <p>SLA "thực hiện" theo p15 §15.4.1: {@code ACCESS_EXPORT}/{@code RECTIFY} 10 ngày,
 * {@code RESTRICT}/{@code OBJECT}/{@code WITHDRAW_CONSENT}/{@code PROTECTION_MEASURE}/
 * {@code COMPLAINT} 15 ngày, {@code ERASE} 20 ngày. Mọi loại đều phản hồi 02 ngày làm
 * việc.</p>
 */
public enum DsarRequestType {
    /** Xuất dữ liệu cá nhân (p15 §15.4.5). */
    ACCESS_EXPORT,
    /** Chỉnh sửa dữ liệu. */
    RECTIFY,
    /** Xoá dữ liệu / xoá tài khoản (p15 §15.4.6). */
    ERASE,
    /** Hạn chế xử lý (p15 §15.4.7). */
    RESTRICT,
    /** Phản đối xử lý. */
    OBJECT,
    /** Rút đồng ý — cùng cơ chế toggle consent. */
    WITHDRAW_CONSENT,
    /** Yêu cầu áp dụng biện pháp bảo vệ DLCN. */
    PROTECTION_MEASURE,
    /** Khiếu nại. */
    COMPLAINT
}
