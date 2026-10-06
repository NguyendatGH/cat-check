package com.catcheck.colorchart.application;

import java.util.UUID;

/**
 * Ai làm, vì sao, từ đâu — bộ thông tin mà mọi hành động có ký hiệu {@code Aud} + {@code Rsn}
 * của p8 §8.4.12 phải ghi vào {@code audit_log} (p4 §4.6.3, p15 REQ-AUD-03).
 *
 * <p>Là một record ở tầng {@code ..application..} chứ không phải tham số rời: sáu tham số rời
 * cùng kiểu {@code String} là lời mời truyền sai thứ tự, và lỗi đó chỉ lộ ra khi có người đọc
 * lại {@code audit_log} nhiều tháng sau — đúng lúc cần nó nhất.
 *
 * @param actorId   người thực hiện
 * @param actorRole vai trò MẠNH NHẤT đã cho phép hành động đi qua (snapshot, p4 §4.6.3)
 * @param reason    lý do đã {@code strip()}, ≥ 10 ký tự (kiểm ở tầng api)
 * @param requestId {@code X-Request-Id} để nối với log ứng dụng (p8 §8.1.9)
 * @param ipAddress IP nguồn
 * @param userAgent User-Agent nguồn
 */
public record AdminAction(
        UUID actorId,
        String actorRole,
        String reason,
        String requestId,
        String ipAddress,
        String userAgent
) {
}
