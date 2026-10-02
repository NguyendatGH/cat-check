package com.catcheck.privacy.domain;

/**
 * Loại tài liệu pháp lý — giá trị nguyên văn lưu ở {@code policy_version.policy_type}
 * (p4 §4.4.3 nhóm B). Enum trên dây = enum trong DB, giống hệt từng ký tự.
 *
 * <p>{@code COMMUNITY_RULES}, {@code RETURNS}, {@code PAYMENT} chỉ dùng từ Phase 2/3
 * (p15 §15.8.1) nhưng giá trị CHECK đã nằm trong V6 — bỏ giá trị khỏi CHECK khi còn
 * dữ liệu làm migration fail (p4 §4.4.1).</p>
 */
public enum PolicyType {
    TERMS,
    PRIVACY,
    COOKIE,
    MEDICAL_DISCLAIMER,
    COMMUNITY_RULES,
    RETURNS,
    PAYMENT
}
