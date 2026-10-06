package com.catcheck.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Than cua L65 {@code POST /admin/jobs/{jobName}/run} (p8 §8.4.12 muc (f)).
 *
 * <p><b>{@code reason} bat buoc</b> — cot Auth cua L65 co ky hieu {@code Rsn}, va p15 REQ-AUD-03
 * chot "request thieu {@code reason} bi tu choi {@code 400}". {@code @NotBlank} chi chan truong
 * rong; do dai toi thieu 10 ky tu do {@code AdminGuard.requireReason} kiem, vi thong diep loi
 * phai la {@code 400 REASON_REQUIRED} (p8 §8.3.2) chu khong phai
 * {@code VALIDATION_FAILED} chung.</p>
 *
 * <p><b>{@code dryRun} la {@code Boolean} (kieu boc), khong {@code boolean}</b> — H15.99: DTO
 * record trong {@code ..api.dto..} khong dung kieu nguyen thuy. Khong {@code @NotNull}: khong
 * gui thi mac dinh {@code false} (chay that), dung ky vong cua nguoi bam nut "Chay lai".</p>
 */
public record RunJobRequest(
        Boolean dryRun,
        @NotBlank @Size(max = 500) String reason) {

    /** {@code true} chi khi client gui tuong minh. */
    public boolean dryRunOrFalse() {
        return Boolean.TRUE.equals(dryRun);
    }
}
