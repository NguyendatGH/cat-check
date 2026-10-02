package com.catcheck.identity.infrastructure.crypto;

import com.catcheck.identity.domain.port.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * {@link PasswordHasher} tren BCrypt (p11 S4, {@code bcryptStrength = 12}).
 *
 * <p>Prefix {@code {bcrypt}} do chinh class nay them vao chu khong phai
 * {@code DelegatingPasswordEncoder} them: {@code UserIdentity.bcryptPrefix()} dung chung
 * quy uoc, va xac thuc dung {@code BCryptPasswordEncoder.matches} tren phan con lai nen
 * khong phai tach prefix thu cong o moi noi.</p>
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;
    private final String dummyHash;

    /**
     * @param bcryptStrength 4..16, xem {@code catcheck.auth.bcrypt-strength} (mac dinh 12).
     */
    public BCryptPasswordHasher(
            @org.springframework.beans.factory.annotation.Value("${catcheck.auth.bcrypt-strength:12}")
            int bcryptStrength) {
        if (bcryptStrength < 4 || bcryptStrength > 16) {
            throw new IllegalArgumentException(
                    "catcheck.auth.bcrypt-strength phai trong [4, 16], nhung " + bcryptStrength);
        }
        this.encoder = new BCryptPasswordEncoder(bcryptStrength);
        // Bam mot lan khi khoi dong: ham con lai KHONG duoc ton thoi gian BCrypt, neu
        // khong thi nhanh "khong tim thay tai khoan" nhanh hon nhanh "sai mat khau" va
        // kiem chung duoc tai khoan ton tai bang dong ho (p11 §11.1.5).
        this.dummyHash = encoder.encode("catcheck-timing-equaliser-not-a-real-password");
    }

    @Override
    public String hash(String rawPassword) {
        return addPrefix(encoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        return encoder.matches(rawPassword, stripPrefix(storedHash));
    }

    @Override
    public String dummyHash() {
        return dummyHash;
    }

    private static String addPrefix(String bcrypt) {
        return com.catcheck.identity.domain.UserIdentity.bcryptPrefix() + bcrypt;
    }

    private static String stripPrefix(String stored) {
        String prefix = com.catcheck.identity.domain.UserIdentity.bcryptPrefix();
        return stored.startsWith(prefix) ? stored.substring(prefix.length()) : stored;
    }
}
