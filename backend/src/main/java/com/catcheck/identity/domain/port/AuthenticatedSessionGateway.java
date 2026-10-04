package com.catcheck.identity.domain.port;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cong nghiep vu cho phien dang nhap HTTP (p11 §11.1.3).
 *
 * <p>Ly do tach cong nay ra khoi {@code application}: nguon su that ve phien la
 * <b>Spring Session JDBC</b> ({@code SPRING_SESSION} / {@code SPRING_SESSION_ATTRIBUTES}),
 * khong phai {@code user_device_session} — bang sau chi la ban sao de hien thi va audit
 * (xem {@code docs/handovers/A1.md}). Nhung Spring Security la thu vien cua tang web nen
 * {@code application} khong duoc import; cong nay giu cho xung dot do khong lan sang
 * nghiep vu.</p>
 */
public interface AuthenticatedSessionGateway {

    /**
     * Mo phien dang nhap cho {@code userId} va ghi {@code user_device_session}.
     *
     * <p>Phai <b>doi session id</b> (session fixation defence) truoc khi gan danh tinh —
     * p11 §11.1.3. Do la ly do interface nay co mat, vi chi tang web moi lam duoc.</p>
     *
     * @param rememberMe {@code true} neu client yeu cau phien dai (30 ngay thay vi 12 gio)
     */
    void establish(UUID userId, String email, Set<String> roles, boolean rememberMe);

    /** Huy phien hien tai (dang nhap). Idempotent. */
    void invalidateCurrent();

    /**
     * Thiet lap lai phien dang nhap sau khi step-up thanh cong (p11 §11.12.4).
     *
     * <p>Khong doi {@code auth_time} nen absolute lifetime van tinh tu luc dang nhap
     * ban dau; chi nang {@code reauth_ok_until} de thao tac nhay cam trong {@code window}
     * duoc phep.</p>
     */
    void markReauthenticated(int windowSeconds);

    /**
     * Nâng {@code mfaLevel} của phiên hiện tại lên {@code TOTP} sau khi A10 (bước 2 đăng nhập)
     * hoặc A11 (mã khôi phục) xác thực thành công — p11 §11.12.1.
     *
     * <p>Không có bước này thì {@code mfaLevel} chỉ là một chuỗi trong JSON trả về cho client
     * và server không bao giờ biết phiên nào đã qua TOTP, nên bộ gác {@code /api/v1/admin/**}
     * (p8 §8.4.12) không thể tồn tại.</p>
     *
     * <p>Không đổi {@code authenticatedAt}: absolute lifetime vẫn tính từ lần đăng nhập đầu,
     * đúng như {@link #markReauthenticated}.</p>
     */
    void markMfaTotpVerified();

    /** {@code userId} tu phien hien tai, hoac rong neu chua dang nhap. */
    Optional<UUID> currentUserId();

    /**
     * Email da xac thuc trong phien hien tai.
     *
     * <p>Khong sua duoc tu client: p11 §11.12.4 quy dinh client <b>khong gui them gi</b> o
     * request sau step-up, server tu doi chieu thuoc tinh phien. Do do khong co setter.</p>
     */
    Optional<String> currentEmail();

    /** Hash cua session id hien tai, dung de danh dau "chinh phien nay". */
    Optional<String> currentSessionIdHash();

    /** Co hieu luc step-up trong cua so thoi gian khong. */
    boolean isReauthenticated();
}
