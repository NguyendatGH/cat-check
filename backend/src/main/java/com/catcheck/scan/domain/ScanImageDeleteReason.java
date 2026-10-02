package com.catcheck.scan.domain;

/** Lý do xoá file ảnh gốc — cột {@code scan_image.delete_reason} (p4 D2). */
public enum ScanImageDeleteReason {
    RETENTION,
    USER_REQUEST,
    ACCOUNT_DELETION
}
