package com.catcheck.ai.application.spi;

import java.util.UUID;

/**
 * Cổng đọc trạng thái tài khoản cho {@code AiChatService} — cùng khuôn
 * {@code community.application.spi.UserAccountPort}, hiện thực bằng JDBC thô trong
 * {@code ai.infrastructure.persistence}.
 */
public interface UserAccountPort {

    /**
     * @return trạng thái hiện tại, hoặc {@code null} nếu không có tài khoản với id này
     */
    AccountStatus statusOf(UUID userId);
}
