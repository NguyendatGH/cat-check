package com.catcheck.identity.infrastructure.crypto;

import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.port.OtpCodeHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Component
public class HmacOtpCodeHasher implements OtpCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final PepperRing ring;

    public HmacOtpCodeHasher(@Value("${OTP_PEPPER:}") String pepper,
                             @Value("${OTP_PEPPER_VERSION:}") String version) {
        this.ring = PepperRing.of("OTP_PEPPER", pepper, version);
    }

    @Override
    public String hash(int pepperVersion, OtpPurpose purpose, String email, String code) {
        return HexFormat.of().formatHex(mac(pepperVersion, purpose, email, code));
    }

    @Override
    public int currentPepperVersion() {
        return ring.currentVersion();
    }

    @Override
    public String hashForStoredVersion(int pepperVersion, OtpPurpose purpose, String email, String code) {
        if (!ring.knows(pepperVersion)) {
            throw new IllegalStateException(
                    "OTP co pepper_version=" + pepperVersion + " ma keyring hien tai khong co ("
                            + ring.knownVersions().keySet() + ")");
        }
        return hash(pepperVersion, purpose, email, code);
    }

    private byte[] mac(int pepperVersion, OtpPurpose purpose, String email, String code) {
        byte[] key = ring.keyFor(pepperVersion);
        byte[] message = (purpose.name() + email + code).getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(message);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Khong tinh duoc HMAC-SHA256 cho ma OTP", ex);
        }
    }
}
