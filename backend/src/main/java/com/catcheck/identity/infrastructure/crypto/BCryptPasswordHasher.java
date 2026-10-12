package com.catcheck.identity.infrastructure.crypto;

import com.catcheck.identity.domain.port.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;
    private final String dummyHash;

    public BCryptPasswordHasher(
            @org.springframework.beans.factory.annotation.Value("${catcheck.auth.bcrypt-strength:12}")
            int bcryptStrength) {
        if (bcryptStrength < 4 || bcryptStrength > 16) {
            throw new IllegalArgumentException(
                    "catcheck.auth.bcrypt-strength phai trong [4, 16], nhung " + bcryptStrength);
        }
        this.encoder = new BCryptPasswordEncoder(bcryptStrength);
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
