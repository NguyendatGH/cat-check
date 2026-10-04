package com.catcheck.notification.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng trong hộp thư in-app (p8 §8.4.7 G4).
 *
 * <p><b>p8 §8.5 không có bảng request/response cho nhóm G</b> (§8.5 chỉ đặc tả nhóm A, B, D, E),
 * nên tên field ở đây suy ra từ cột của p4 F2 theo đúng quy ước camelCase của p8 §8.1.2. Nếu
 * sau này p8 bổ sung bảng cho nhóm G thì bảng đó thắng.</p>
 *
 * @param title    {@code title_snapshot} — nội dung ĐÃ render lúc phát, nên inbox vẫn đúng câu
 *                 chữ cũ sau khi template đổi (p12 §12.4)
 * @param deepLink route của p9 §9.4; client điều hướng thẳng tới đây
 */
public record NotificationView(
        UUID id,
        String templateCode,
        String title,
        String body,
        String deepLink,
        String refType,
        UUID refId,
        boolean read,
        Instant readAt,
        Instant createdAt) {
}
