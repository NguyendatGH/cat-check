package com.catcheck.credit.domain;

/**
 * Trạng thái suy diễn cho màn admin, không lưu DB: {@code EXPIRED} = cột status vẫn ISSUED nhưng
 * {@code valid_until} đã qua nên không còn kích hoạt được. ISSUED ở đây chỉ còn nghĩa "chưa dùng, còn hạn".
 */
public enum ActivationCodeEffectiveStatus {

    ISSUED,

    REDEEMED,

    VOID,

    EXPIRED
}
