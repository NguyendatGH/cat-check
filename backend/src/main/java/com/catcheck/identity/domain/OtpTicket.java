package com.catcheck.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Ticket tra ve sau khi xac thuc OTP dung (p11 §11.2.2).
 *
 * <p>Chuoi ngau nhien &ge;128 bit, TTL 10 phut, dung mot lan, gan chat voi
 * {@code (email, purpose)} qua {@code email_otp.ticket_hash}. Chi luu SHA-256
 * cua ticket trong DB; ban tho chi ton tai trong response cua {@code POST /auth/otp/verify}.</p>
 *
 * <p>Ticket <b>khong co pepper</b> va khong can: nguon cua no la
 * {@code SecureRandom} 128 bit chu khong phai mat khau nguoi dung, va no da bi
 * {@code HMAC(pepper, OTP)} bao ve o buoc truoc. Hash SHA-256 o day chi de DB khong
 * chua duoc ticket de dung lai.</p>
 */
public record OtpTicket(String rawValue, Instant expiresAt) {

    /** p11 §11.2.2: &ge;128 bit = 16 byte. */
    public static final int BYTE_LENGTH = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    public OtpTicket {
        Objects.requireNonNull(rawValue, "ticket khong duoc null");
        Objects.requireNonNull(expiresAt, "han dung khong duoc null");
    }

    /** Sinh ticket moi. Dung {@link SecureRandom}, khong {@code UUID.randomUUID()} de
     *  tranh phu thuoc vao {@code SecureRandom} cua JDK va de reviewer thay ro nguon
     *  ngau nhien (R12 {@code RandomnessRuleTests}). */
    public static String newRawValue() {
        byte[] bytes = new byte[BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Tao ticket moi da gan han dung. */
    public static OtpTicket create(Instant expiresAt) {
        return new OtpTicket(newRawValue(), expiresAt);
    }

    /**
     * SHA-256 hex cua ticket — gia tri luu vao {@code email_otp.ticket_hash}.
     *
     * <p>Khong phai {@code equals} giữa hai {@code OtpTicket}: so sanh phai dien ra tren
     * bản hash trong DB, con ban tho chi nam tren response cua client.</p>
     */
    public static String hashOf(String rawTicket) {
        Objects.requireNonNull(rawTicket, "ticket khong duoc null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawTicket.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 bat buoc phai ton tai theo JCA; thieu no la moi truong hong.
            throw new IllegalStateException("JCA khong co SHA-256", ex);
        }
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
