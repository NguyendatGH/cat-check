package com.catcheck.identity.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Email da chuan hoa. Bat bien dung chung cho khoa chinh {@code app_user.email},
 * cho {@code user_identity.provider_user_id} cua LOCAL, va cho input cua
 * {@code HMAC-SHA256(pepper, purpose || email || code)} — neu hai noi dung hai
 * dang chuan hoa khac nhau thi OTP/OTP-ticket se khong khop (p11 §11.2.1).
 */
public record EmailAddress(String value) {

    /**
     * RFC 5321: moi local-part truoc {@code @} toi da la 64 ky tu, con ca dia chi
     * toi da 254. Dung de chan rong truoc khi ghi vao DB (p8 §8.1 kiem tra do dai).
     */
    private static final int MAX_LENGTH = 254;

    /**
     * Duoi: mot {@code @}, khong bat dau/ket thuc bang {@code .}, khong co
     * {@code ..}, domain phai co it nhat mot dau cham. KHONG co kip hoac dau
     * thuong trong {@code code} de tranh hinh thuc sai ma hoa.
     */
    private static final Pattern SHAPE = Pattern.compile(
            "^[^\\s@]{1,64}@[^\\s@.]+(\\.[^\\s@.]+)+$");

    public EmailAddress {
        Objects.requireNonNull(value, "email khong duoc null");
        String normalized = normalize(value);
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Email qua dai: toi da " + MAX_LENGTH + " ky tu");
        }
        if (!SHAPE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Email khong hop le");
        }
        value = normalized;
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw);
    }

    /** {@code tryParse} cho noi khong duoc pham loi (tra ve {@code null} thay vi nem). */
    public static EmailAddress parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new EmailAddress(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Lowercase + trim. KHONG dung {@code java.net.IDN} hay kich thuc unicode:
     * p11 §11.2.1 chi yeu cau chuan hoa de so sanh hang, va bien doi them se
     * lam cho gia tri trong DB khong khop gia tri nguoi dung nhin thay.
     */
    private static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value;
    }
}
