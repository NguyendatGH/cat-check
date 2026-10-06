package com.catcheck.credit.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/admin/users/{userId}/credit-adjustments} (p8 L10, p14 §14.4.4).
 *
 * <p><b>Không có trường nguyên thuỷ nào, và đó là bắt buộc</b> (H15.99): Jackson 3 truyền
 * {@code null} cho property vắng mặt, {@code FAIL_ON_NULL_FOR_PRIMITIVES} ném ngay ở bước bind ⇒
 * request thiếu field trả <b>500</b> thay vì 400. Dùng kiểu bọc + {@code @NotNull} để lỗi thiếu
 * field là lỗi validate thật.</p>
 *
 * <p>{@code amount} luôn <b>dương</b>; chiều do {@code direction} quyết định. Hai cách diễn đạt
 * cùng một ý (số âm <i>và</i> một enum chiều) là hai cách để sai.</p>
 *
 * @param direction    {@code GRANT} (cấp thêm) hoặc {@code REVOKE} (thu hồi theo FEFO)
 * @param amount       số credit, luôn dương; trần thật nằm ở {@code app_setting} (p14 bước 5) —
 *                     {@code @Max} ở đây chỉ chặn số vô lý trước khi tốn một lần tra DB
 * @param packageCode  gói <b>tham chiếu</b> cho lô mới (chiều {@code GRANT}); bỏ qua ở
 *                     {@code REVOKE}. Bắt buộc vì {@code credit_batch.package_code} là NOT NULL
 *                     (p4 nhóm E)
 * @param validityDays số ngày hiệu lực của lô mới; {@code null} ⇒ lấy
 *                     {@code package_plan.credit_validity_days} (p14 bước 3: "gói tham chiếu")
 * @param reason       lý do, bắt buộc ≥ 10 ký tự — <b>ràng buộc API</b>, không phải nhắc nhở UI
 *                     (p15 REQ-AUD-03, p14 bước 4)
 */
public record CreditAdjustmentRequest(
        @NotBlank @Pattern(regexp = "GRANT|REVOKE") String direction,
        @NotNull @Min(1) @Max(100_000) Integer amount,
        @Size(max = 32) String packageCode,
        @Min(1) @Max(3_650) Integer validityDays,
        @NotBlank @Size(min = 10, max = 500) String reason
) {
}
