package com.catcheck.credit.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body của L20 {@code POST /admin/activation-codes/batch}.
 *
 * <p>{@code quantity} có {@code @Max} để bean validation chặn sớm, nhưng trần nghiệp vụ thật
 * nằm ở tầng application và trả {@code 422 ACTIVATION_BATCH_TOO_LARGE} (p8 §8.2.4) — hai lớp
 * khác nhau: lớp này bảo vệ server khỏi một con số vô lý, lớp kia là hợp đồng API.</p>
 *
 * <p>Hai số dùng {@code Integer} + {@code @NotNull} chứ không {@code int}: với record, Jackson 3
 * truyền {@code null} cho property vắng mặt và {@code FAIL_ON_NULL_FOR_PRIMITIVES} ném
 * {@code HttpMessageNotReadableException} ⇒ <b>500</b> thay vì {@code 400} với tên trường còn
 * thiếu. Bắt được thật trên {@code UpdatePackagePlanRequest} (xem javadoc ở đó).</p>
 *
 * @param packageCode     mã gói trong {@code package_plan}
 * @param quantity        số mã cần sinh
 * @param productionBatch định danh lô sản xuất, đồng thời là {@code batchId} của lô này
 * @param validForDays    hạn KÍCH HOẠT tính từ lúc phát hành (khác hạn credit — p5 §5.5)
 * @param reason          ký hiệu {@code Rsn} của p8: tối thiểu 10 ký tự, ghi vào {@code audit_log}
 */
public record IssueActivationBatchRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Z0-9_]{1,32}") String packageCode,
        @NotNull @Min(1) @Max(50_000) Integer quantity,
        @NotBlank @Size(max = 64) String productionBatch,
        @NotNull @Min(1) @Max(3_650) Integer validForDays,
        @NotBlank @Size(min = 10, max = 500) String reason
) {
}
