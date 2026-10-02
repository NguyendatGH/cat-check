package com.catcheck.identity.application;

import com.catcheck.shared.security.SecurityPrincipal;

import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Danh tinh nguoi dung dang chay trong phien hien tai.
 *
 * <p>Nam o {@code ..application..} chu khong phai {@code ..infrastructure..} de
 * controller ({@code ..api..}) doc duoc ma khong phu thuoc tang ha tang (R3).</p>
 *
 * <p>Lưu ý: p11 §11.1.3 yeu cau cookie phiên <b>opaque</b>, và day là thứ duy nhất
 * client nắm giữ. `userId` trong object này chỉ nằm trong
 * {@code SPRING_SESSION_ATTRIBUTES} phía server — client không đọc được, và cũng không
 * cần: moi thao tac deu xác thực qua filter chain.</p>
 *
 * <p>{@code implements Principal}: bug that da sua — khong co no,
 * {@code UsernamePasswordAuthenticationToken.getName()} roi ve {@code toString()} cua record
 * (in het moi field, de vuot 100 ky tu), va Spring Session ghi gia tri do vao
 * {@code SPRING_SESSION.PRINCIPAL_NAME VARCHAR(100)} — nem {@code DataIntegrityViolationException
 * value too long}. Xac nhan that ngay lan dau {@code establish()} thuc su chay (truoc do
 * khong ai goi toi, xem {@code AuthController.register()}). {@code getName()} tra ve
 * {@code userId} (opaque, khop chu thich tren) thay vi email — khong lo email vao cot nay.</p>
 *
 * <p>{@code implements SecurityPrincipal}: hop dong dung chung xuyen module
 * ({@code shared.security}) — 9 controller ngoai identity nhan principal qua kieu do. Thieu
 * no thi {@code @AuthenticationPrincipal SecurityPrincipal} resolve ra {@code null} va moi
 * endpoint can dang nhap tra 500 (xem javadoc {@link SecurityPrincipal}).</p>
 *
 * @param reauthenticated cho biết step-up còn hiệu lực hay không (p11 §11.12.4)
 */
public record AuthPrincipal(
        UUID userId,
        String email,
        Set<String> roles,
        Instant authenticatedAt,
        boolean reauthenticated) implements Serializable, Principal, SecurityPrincipal {

    public AuthPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    @Override
    public String getName() {
        return userId.toString();
    }

    public static AuthPrincipal of(UUID userId, String email, Set<String> roles, Instant now) {
        return new AuthPrincipal(userId, email, roles, now, false);
    }

    public AuthPrincipal withReauthenticated(boolean value) {
        return new AuthPrincipal(userId, email, roles, authenticatedAt, value);
    }
}
