package com.catcheck.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Than cua L70 {@code PATCH /admin/settings/{key}} (p8 §8.4.12 muc (f)).
 *
 * <p><b>{@code value} la String, khong phai {@code Object}.</b> Cot {@code app_setting.value} la
 * {@code JSONB} (p4 §H3) nhung {@code value_type} cua chinh dong do quyet dinh cach boc
 * ({@code STRING} | {@code INT} | {@code BOOL} | {@code JSON}), va adapter ep kieu o DB. Nhan
 * {@code Object} se de Jackson suy kieu tu JSON cua client — tuc la client quyet dinh kieu, trong
 * khi kieu da duoc chot trong DB; mot {@code 5} va mot {@code "5"} se ghi ra hai gia tri khac
 * nhau cho cung mot khoa.
 *
 * <p><b>Khong co truong {@code valueType} va {@code secret}.</b> L70 la "sua MOT khoa cau hinh",
 * khong phai dinh nghia lai khoa: danh muc khoa + kieu + co {@code secret} thuoc p4 §H3 va seed
 * {@code R__seed_app_setting.sql}. Cho doi {@code secret} qua API nghia la mot khoa dang duoc che
 * co the bi mo bang mot request.
 *
 * @param reason ky hieu {@code Rsn} cua p8 §8.3.2; toi thieu 10 ky tu, kiem o
 *               {@code AdminGuard.requireReason} de tra dung {@code 400 REASON_REQUIRED}
 */
public record UpdateAppSettingRequest(
        @NotNull @Size(max = 10_000) String value,
        @NotBlank @Size(max = 500) String reason) {
}
