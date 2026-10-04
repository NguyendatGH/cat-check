package com.catcheck.privacy.application;

import com.catcheck.privacy.spi.RegistrationConsentEvent;
import com.catcheck.privacy.domain.ConsentMethod;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Nghe {@code RegistrationConsentEvent} để ghi {@code consent_record} cho các lựa chọn
 * đồng ý người dùng tick ngay tại màn đăng ký (p15 §15.3.3 — {@code POST /auth/register}).
 *
 * <p><b>Vì sao ở đây, không phải trong identity:</b> {@code identity/package-info.java} chỉ cho
 * phép phụ thuộc {@code privacy::spi}, không phải {@code privacy::api}/{@code privacy}
 * (một chiều: {@code privacy} phụ thuộc {@code identity}, không phải ngược lại — xem
 * {@code privacy/package-info.java} đã liệt kê {@code identity} plain trong
 * {@code allowedDependencies}). {@code identity} chỉ biết publish sự kiện với consent
 * <b>thô</b> (purposeCode + granted) và ngữ cảnh request; {@code privacy} mới biết
 * {@code policy_version} hiện hành để tính {@code policyHash}/{@code consentTextHash}
 * (p4 §4.4.3 nhóm B2) — đúng ranh giới sở hữu miền.</p>
 *
 * <p><b>Đồng bộ, cùng transaction</b> với {@code RegistrationService.register}: đây là
 * {@code @EventListener} THƯỜNG, không
 * {@code @Async}, không {@code @TransactionalEventListener} — Spring gọi listener này ngay
 * trong cùng thread/transaction JDBC đang mở của {@code RegistrationService}, nên
 * {@link ConsentService#recordConsents} (đã tự {@code @Transactional}, propagation
 * {@code REQUIRED} mặc định) chỉ tham gia (join) transaction đó chứ không mở transaction mới.
 * Nếu ghi consent lỗi, toàn bộ giao dịch đăng ký — kể cả việc tạo {@code app_user} — rollback
 * theo, đúng tinh thần "không tạo tài khoản mà thiếu bằng chứng đồng ý" (Điều 6.2 NĐ356,
 * p15 §15.3.1 C7).</p>
 *
 * <p><b>Idempotent theo thiết kế của {@link ConsentService#recordConsents}:</b> nếu cùng một
 * {@code (userId, purposeCode)} đã có bản ghi mới nhất cùng trạng thái (GRANTED hoặc
 * DENIED/WITHDRAWN), phương thức đó KHÔNG insert thêm dòng nào — nên dù event publication
 * registry của Spring Modulith redeliver (hạ tầng {@code V4__modulith_event_publication.sql})
 * hay listener này vô tình được gọi lại, sẽ không sinh bản ghi trùng trong
 * {@code consent_record}.</p>
 *
 * <p><b>Lưu ý vận hành:</b> phương thức bỏ qua ngay (không gọi {@code recordConsents}) khi
 * {@code event.consents()} rỗng/null — khớp {@code RegistrationService} chỉ phát sự kiện khi
 * request đăng ký có kèm consent (lần gọi lại {@code register} kèm {@code otpTicket} để kích
 * hoạt tài khoản không kèm consent, FE không gửi lại).</p>
 */
@Component
public class UserRegisteredEventListener {

    /** Khớp giá trị mẫu {@code ui_surface} liệt kê ở p4 §4.4.3 nhóm B2 cho điểm chạm đăng ký. */
    private static final String SURFACE_REGISTER = "register";
    private static final String DEFAULT_LOCALE = "vi";

    private final ConsentService consentService;

    public UserRegisteredEventListener(ConsentService consentService) {
        this.consentService = consentService;
    }

    @EventListener
    public void onUserRegistered(RegistrationConsentEvent event) {
        if (event.consents() == null || event.consents().isEmpty()) {
            return;
        }
        List<ConsentGrant> grants = event.consents().stream()
                .map(grant -> new ConsentGrant(grant.purposeCode(), grant.granted()))
                .toList();
        String locale = event.locale() != null && !event.locale().isBlank()
                ? event.locale() : DEFAULT_LOCALE;
        consentService.recordConsents(
                event.userId(),
                grants,
                ConsentMethod.WEB_CHECKBOX,
                SURFACE_REGISTER,
                locale,
                RequestEvidence.of(event.requestId(), event.ipAddress(), event.userAgent()));
    }
}
