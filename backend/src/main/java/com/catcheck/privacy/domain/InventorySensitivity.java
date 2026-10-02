package com.catcheck.privacy.domain;

/**
 * Độ nhạy của một mục trong bảng kiểm kê dữ liệu (p4 §4.4.3 nhóm B, p15 §15.2.2):
 * {@code BASIC} = dữ liệu cá nhân cơ bản (Đ3.11 NĐ356), {@code SENSITIVE} = dữ liệu
 * nhạy cảm (Đ4 NĐ356).
 */
public enum InventorySensitivity {
    BASIC,
    SENSITIVE
}
