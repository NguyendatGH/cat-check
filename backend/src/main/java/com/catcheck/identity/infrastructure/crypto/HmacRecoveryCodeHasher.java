package com.catcheck.identity.infrastructure.crypto;

import com.catcheck.identity.domain.port.RecoveryCodeHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * {@code HMAC-SHA256(recovery_pepper, code)} — p11 §11.12.3.
 *
 * <p>Pepper doc tu bien moi truong {@code RECOVERY_CODE_PEPPER} +
 * {@code RECOVERY_CODE_PEPPER_VERSION}, fail-fast trong constructor neu thieu
 * (cung chu dich voi {@link HmacOtpCodeHasher}).</p>
 */
@Component
public class HmacRecoveryCodeHasher implements RecoveryCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final PepperRing ring;

    public HmacRecoveryCodeHasher(@Value("${RECOVERY_CODE_PEPPER:}") String pepper,
                                  @Value("${RECOVERY_CODE_PEPPER_VERSION:}") String version) {
        this.ring = PepperRing.of("RECOVERY_CODE_PEPPER", pepper, version);
    }

    @Override
    public String hash(int pepperVersion, String recoveryCode) {
        byte[] key = ring.keyFor(pepperVersion);
        byte[] message = recoveryCode.getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Khong tinh duoc HMAC-SHA256 cho ma khoi phuc", ex);
        }
    }

    @Override
    public int currentPepperVersion() {
        return ring.currentVersion();
    }

    @Override
    public boolean matches(String recoveryCode, String codeHash, int pepperVersion) {
        if (!ring.knows(pepperVersion)) {
            throw new IllegalStateException(
                    "Ma khoi phuc co pepper_version=" + pepperVersion
                            + " ma keyring hien tai khong co (" + ring.knownVersions().keySet() + ")");
        }
        String candidate = hash(pepperVersion, recoveryCode);
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                codeHash.getBytes(StandardCharsets.UTF_8));
    }
}
