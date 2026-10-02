package com.catcheck.identity.application.privacy;

import com.catcheck.identity.domain.port.AuthenticatedSessionGateway;
import com.catcheck.privacy.spi.StepUpVerificationPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter A1 → A2: implement {@link StepUpVerificationPort} (named interface {@code privacy::spi}
 * — xem {@code docs/handovers/A1-A2-integration.md}).
 *
 * <p>Nguồn sự thật duy nhất về "đã reauth" là {@link AuthenticatedSessionGateway#isReauthenticated()},
 * đọc cờ {@code AuthPrincipal.reauthenticated} mà {@code SessionService#reauthenticate}
 * ({@code POST /auth/reauth}, p11 §11.12.4) ghi vào {@code SecurityContext} của phiên hiện tại
 * qua {@code AuthenticatedSessionGateway#markReauthenticated(300)}.</p>
 *
 * <p><b>Fail-closed có chủ đích, kể cả khi không có exception nào xảy ra:</b></p>
 * <ul>
 *   <li>Không có {@code Authentication} nào trong {@code SecurityContext} hiện tại (gọi từ
 *       job/async ngoài request HTTP) ⇒ {@link AuthenticatedSessionGateway#currentUserId()}
 *       trả rỗng ⇒ {@code false}. Không throw, không mặc định {@code true}.</li>
 *   <li><b>{@code currentUserId()} phải khớp đúng {@code userId} được hỏi</b> — không tin cờ
 *       {@code reauthenticated} của "phiên hiện tại" một cách mù quáng nếu nó không phải phiên
 *       của chính user đang được kiểm tra. Đây không phải tình huống thực tế xảy ra ở luồng tự
 *       phục vụ DSAR hiện tại (controller luôn gọi bằng chính user đang đăng nhập), nhưng khớp
 *       đúng nguyên tắc fail-closed mà javadoc gốc của cổng yêu cầu.</li>
 * </ul>
 *
 * <p><b>Giới hạn đã biết (ngoài phạm vi bàn giao A1↔A2 này, KHÔNG được sửa ở đây vì đụng
 * {@code SessionService}/{@code AuthPrincipal}/{@code AuthController} nằm ngoài vùng cho phép):</b>
 * cơ chế reauth hiện tại của identity là một cờ boolean đơn (`AuthPrincipal.reauthenticated`)
 * không mang mốc hết hạn lẫn phạm vi hành động — {@code SpringSecuritySessionGateway#markReauthenticated}
 * nhận tham số {@code windowSeconds} nhưng KHÔNG dùng nó để đặt hạn, và {@code scope} của
 * {@link #isVerified(UUID, String)} (được truyền nguyên văn "DATA_EXPORT"/"ACCOUNT_ERASE") không
 * được đối chiếu với hành động đã step-up — nghĩa là step-up cho MỘT hành động nhạy cảm hiện đang
 * hợp lệ cho MỌI scope khác trong cùng phiên cho tới khi đăng xuất/đổi mật khẩu/xoay session id.
 * Đây là khoảng cách so với đặc tả đầy đủ của p11 §11.12.4 (cửa sổ 300 giây + ràng buộc
 * {@code ONE_SHOT}/{@code operationId}) đã tồn tại từ trước trong chính {@code SessionService},
 * không phải lỗi phát sinh từ bàn giao này — ghi lại trong
 * {@code docs/handovers/A1-A2-integration.md} để A1 theo dõi riêng.</p>
 */
@Component
class PrivacyStepUpVerificationAdapter implements StepUpVerificationPort {

    private final AuthenticatedSessionGateway sessionGateway;

    PrivacyStepUpVerificationAdapter(AuthenticatedSessionGateway sessionGateway) {
        this.sessionGateway = sessionGateway;
    }

    @Override
    public boolean isVerified(UUID userId, String scope) {
        return sessionGateway.currentUserId()
                .filter(userId::equals)
                .map(ignored -> sessionGateway.isReauthenticated())
                .orElse(Boolean.FALSE);
    }
}
