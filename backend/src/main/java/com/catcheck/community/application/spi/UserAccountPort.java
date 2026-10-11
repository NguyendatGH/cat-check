package com.catcheck.community.application.spi;

import java.util.UUID;

/**
 * Cổng đọc trạng thái tài khoản, hiện thực bằng JDBC thô trong
 * {@code community.infrastructure.persistence} (xem adapter đó để biết lý do không import
 * {@code identity.*}).
 *
 * <p>Module community <b>không</b> tra cứu {@code app_user} qua type của module khác. Lý do
 * không phải sợ phụ thuộc kỹ thuật mà vì p15/I19 buộc phải có đúng MỘT chỗ quyết định "tài khoản
 * này có được xử lý không", và {@code community.application.CommunityService} phải tự kiểm tra
 * trước khi ghi (bài đăng, bình luận, reaction, report) — không có cổng nào khác làm việc này
 * thay cho community.</p>
 */
public interface UserAccountPort {

    /**
     * @return trạng thái hiện tại, hoặc {@code null} nếu không có tài khoản với id này — trường hợp
     *         cuối cùng xảy ra khi dữ liệu bị xoá cứng theo yêu cầu bảo vệ dữ liệu
     */
    AccountStatus statusOf(UUID userId);
}
