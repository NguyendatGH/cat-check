package com.catcheck.privacy.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Body C3 — cấp/rút một hoặc nhiều purpose trong MỘT request (p8 §8.4.3).
 * Mỗi mục đích là một checkbox riêng (p15 §15.3.1 C2) — không gộp.
 *
 * @param consents danh sách mục khai báo; mục nào không nằm trong danh sách này giữ nguyên
 *                 trạng thái cũ
 */
public record RecordConsentsRequest(
        @NotEmpty(message = "consents không được rỗng")
        @Valid
        List<ConsentGrantRequest> consents
) {
}
