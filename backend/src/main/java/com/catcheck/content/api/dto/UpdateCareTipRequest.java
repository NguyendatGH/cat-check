package com.catcheck.content.api.dto;

import com.catcheck.content.domain.ClaimType;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body của L42 — sửa bản nháp. Toàn bộ trường đều tuỳ chọn: trường vắng mặt nghĩa là giữ nguyên,
 * nên client PATCH từng phần một mà không phải đọc lại bài rồi gửi nguyên khối.
 *
 * <p>{@code claimType} nằm ở đây vì H9.3 yêu cầu việc đổi mức độ tuyên bố phải nằm trong phạm vi
 * sửa bản nháp, và có thay đổi thì bài phải đi duyệt lại từ đầu.</p>
 */
public record UpdateCareTipRequest(
        @Size(min = 1, max = 200)
        String title,

        String summary,
        String bodyMd,
        ClaimType claimType,
        String sourceReference,

        @Size(min = 0, max = 10_000)
        Integer sortWeight,

        List<@Size(max = 40) String> tags
) {
}
