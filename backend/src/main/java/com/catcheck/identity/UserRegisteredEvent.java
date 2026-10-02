package com.catcheck.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vừa tạo tài khoản {@code PENDING_VERIFICATION}, kèm các lựa chọn đồng ý người dùng tick ngay
 * tại màn đăng ký (p15 §15.3.3 — {@code POST /auth/register}, {@code consents: [{purposeCode,
 * granted}]}).
 *
 * <p><b>Vì sao đặt ở gói gốc {@code com.catcheck.identity}, không phải {@code identity.api}
 * (khác tiền lệ {@code cat.api.CatCreatedEvent}):</b> {@code privacy/package-info.java} khai
 * {@code allowedDependencies = {"shared", "identity", "notification", "audit"}} — tên module
 * TRẦN, không phải {@code "identity::api"}. Với Spring Modulith, một mục
 * {@code allowedDependencies} trần (không có {@code "::"}) chỉ cấp quyền truy cập
 * <b>named interface "unnamed"</b> của module đích — tức đúng những type nằm TRỰC TIẾP ở gói gốc
 * của module, KHÔNG bao gồm bất kỳ gói con nào dù có {@code @NamedInterface} hay không (xác
 * minh bằng {@code ApplicationModules.of(...).verify()} thật, không phải suy đoán — đặt sự kiện
 * này ở {@code identity.api} làm {@code ModularityTests} đỏ với thông báo "depends on named
 * interface(s) 'identity :: api' ... Allowed targets: shared, identity, notification, audit").
 * Vì {@code privacy/package-info.java} là file đã có sẵn của module khác (không được sửa —
 * xem {@code docs/handovers/A1-backend-fix.md}), lối đi hợp lệ duy nhất phía {@code identity}
 * là đặt sự kiện ở gói gốc để rơi vào named interface "unnamed" mà {@code privacy} đã được
 * cấp sẵn.</p>
 *
 * <p>Module {@code privacy} (được phép phụ thuộc {@code identity}, xem
 * {@code privacy/package-info.java}) lắng nghe sự kiện này để ghi {@code consent_record} qua
 * {@code ConsentService.recordConsents} — {@code identity} không được gọi thẳng vì
 * {@code identity/package-info.java} CHỈ cho phép phụ thuộc {@code privacy::spi}, không phải
 * {@code privacy::api} (một chiều: privacy phụ thuộc identity, không phải ngược lại).</p>
 *
 * <p>Payload CHỈ mang consent <b>thô</b> (purposeCode + granted) và ngữ cảnh request —
 * {@code policyVersionId}/{@code policyHash}/{@code consentTextHash} (p4 §4.4.3 nhóm B2) chỉ
 * {@code privacy} biết vì chính nó đang publish chính sách hiện hành; listener bên
 * {@code privacy} tự tra {@code policy_version} hiện hành rồi ghi, đúng ranh giới sở hữu miền
 * (identity không biết gì về nội dung chính sách).</p>
 *
 * <p><b>Phát đồng bộ, cùng transaction</b> với {@code RegistrationService.register} — giống hệt
 * tiền lệ {@code scan.api.ScanSavedEvent}: listener phía {@code privacy} là {@code @EventListener}
 * THƯỜNG (không {@code @Async}, không {@code @TransactionalEventListener}), nên chạy ngay trong
 * cùng transaction JDBC; nếu ghi consent lỗi (ví dụ thiếu {@code policy_version} đang hiệu lực),
 * toàn bộ giao dịch — kể cả việc tạo {@code app_user} — rollback theo. Chọn đồng bộ thay vì dùng
 * cơ chế publication registry bất đồng bộ của Spring Modulith (dù hạ tầng đã có từ
 * {@code V4__modulith_event_publication.sql}) vì Điều 6.2 Nghị định 356/2025 đặt nghĩa vụ chứng
 * minh đồng ý lên bên kiểm soát dữ liệu (p15 §15.3.1 C7) — chấp nhận "at-least-once" bất đồng bộ
 * ở đúng bước tạo tài khoản nghĩa là chấp nhận một khoảng thời gian tài khoản tồn tại mà chưa có
 * bằng chứng đồng ý nào được ghi, dù ngắn. Xem {@code docs/handovers/A1-backend-fix.md}.</p>
 *
 * @param userId     tài khoản vừa tạo (đã tồn tại trong transaction hiện tại, insert trước khi
 *                   sự kiện này được publish)
 * @param consents   lựa chọn thô user tick lúc đăng ký; rỗng khi request đăng ký không kèm
 *                   consent nào (ví dụ lần gọi lại {@code register} kèm {@code otpTicket} để
 *                   kích hoạt — FE không gửi lại {@code consents} ở bước đó)
 * @param locale     ngôn ngữ văn bản user thực sự đọc lúc tick — {@code null} ⇒ privacy tự áp
 *                   {@code vi} (mặc định của {@code app_user.locale})
 * @param ipAddress  IP tại thời điểm đăng ký — một phần bằng chứng đồng ý (p4 §4.4.3 nhóm B2)
 * @param userAgent  User-Agent tại thời điểm đăng ký
 * @param requestId  nối với {@code audit_log.request_id} / access log
 * @param occurredAt thời điểm phát, lấy từ {@code Clock} đã inject trong {@code RegistrationService}
 */
public record UserRegisteredEvent(
        UUID userId,
        List<ConsentGrant> consents,
        String locale,
        String ipAddress,
        String userAgent,
        String requestId,
        Instant occurredAt) {

    /** Một mục đồng ý thô — chưa tra policy, chưa tính hash (đó là việc của {@code privacy}). */
    public record ConsentGrant(String purposeCode, boolean granted) {
    }
}
