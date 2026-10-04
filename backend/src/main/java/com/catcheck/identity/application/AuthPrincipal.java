package com.catcheck.identity.application;

import com.catcheck.shared.security.MfaLevel;
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
 * @param mfaLevel        đã qua bước TOTP trong phiên này chưa (p11 §11.12.1). Đăng nhập xong
 *                        luôn là {@link MfaLevel#NONE}; chỉ A10/A11 nâng lên
 *                        {@link MfaLevel#TOTP}. Đây là thứ mà bộ gác {@code /api/v1/admin/**}
 *                        đọc — trước bản sửa này {@code mfaLevel} chỉ tồn tại trong JSON trả về
 *                        của {@code POST /auth/totp/verify} và không được lưu ở đâu cả, nên
 *                        không có cách nào biết một phiên đã qua TOTP hay chưa.
 */
public record AuthPrincipal(
        UUID userId,
        String email,
        Set<String> roles,
        Instant authenticatedAt,
        boolean reauthenticated,
        MfaLevel mfaLevel) implements Serializable, Principal, SecurityPrincipal {

    public AuthPrincipal {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        mfaLevel = mfaLevel == null ? MfaLevel.NONE : mfaLevel;
    }

    @Override
    public String getName() {
        return userId.toString();
    }

    public static AuthPrincipal of(UUID userId, String email, Set<String> roles, Instant now) {
        return new AuthPrincipal(userId, email, roles, now, false, MfaLevel.NONE);
    }

    public AuthPrincipal withReauthenticated(boolean value) {
        return new AuthPrincipal(userId, email, roles, authenticatedAt, value, mfaLevel);
    }

    /** Nâng phiên lên {@link MfaLevel#TOTP} sau khi A10/A11 xác thực thành công. */
    public AuthPrincipal withMfaLevel(MfaLevel value) {
        return new AuthPrincipal(userId, email, roles, authenticatedAt, reauthenticated, value);
    }
}
