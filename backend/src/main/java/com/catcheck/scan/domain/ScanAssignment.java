package com.catcheck.scan.domain;

/**
 * Gán kết quả quét cho mèo — cột {@code scan.assignment} (p4 D1).
 *
 * <p>{@code UNASSIGNED} là trạng thái NỘI BỘ của bản ghi trước khi user xác nhận mèo — API
 * KHÔNG chấp nhận giá trị này ở request (p8 §8.5.4: "assignment: ASSIGNED | SHARED_UNKNOWN.
 * UNASSIGNED không hợp lệ ở API"). {@code CHECK ((assignment='ASSIGNED') = (cat_id IS NOT NULL))}
 * là bất biến DB tương ứng.
 */
public enum ScanAssignment {
    UNASSIGNED,
    ASSIGNED,
    SHARED_UNKNOWN
}
