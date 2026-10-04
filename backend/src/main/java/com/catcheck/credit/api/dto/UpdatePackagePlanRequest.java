package com.catcheck.credit.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body của L26 {@code PATCH /admin/package-plans/{code}}.
 *
 * <p>Ngữ nghĩa PATCH (p8 §8.1.6): field vắng mặt hoặc {@code null} = KHÔNG đổi. Vì vậy mọi số
 * đều là kiểu bọc — {@code int} không phân biệt được "không gửi" với "gửi 0".</p>
 *
 * @param clearMaxCatProfiles đặt {@code maxCatProfiles} về "không giới hạn". Cần một cờ riêng
 *                            vì {@code null} ở {@code maxCatProfiles} đã mang nghĩa "không đổi";
 *                            JSON {@code null} và field vắng mặt không phân biệt được sau khi
 *                            Jackson bind vào record. Kiểu {@code Boolean} chứ không
 *                            {@code boolean}: <b>bug thật đã sửa</b> — với record, Jackson 3
 *                            truyền {@code null} cho property vắng mặt và
 *                            {@code FAIL_ON_NULL_FOR_PRIMITIVES} (mặc định bật) ném
 *                            {@code HttpMessageNotReadableException} ⇒ <b>500</b>. Xác nhận
 *                            bằng curl thật: {@code PATCH} với body
 *                            {@code {"creditAmount":11,"reason":"..."}} trả 500 trước khi
 *                            controller kịp kiểm {@code If-Match}, nên cả nhánh 428 cũng không
 *                            quan sát được.
 */
public record UpdatePackagePlanRequest(
        @Min(1) @Max(100_000) Integer creditAmount,
        @Min(1) @Max(3_650) Integer creditValidityDays,
        @Min(1) @Max(1_000) Integer maxCatProfiles,
        Boolean clearMaxCatProfiles,
        @Valid PlanFeaturesView features,
        Boolean active,
        @NotBlank @Size(min = 10, max = 500) String reason
) {

    /** {@code true} chỉ khi client gửi tường minh {@code true}. */
    public boolean clearMaxCatProfilesFlag() {
        return Boolean.TRUE.equals(clearMaxCatProfiles);
    }
}
