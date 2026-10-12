package com.catcheck.identity.infrastructure.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

final class HkdfKeyDeriver {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int HASH_LENGTH = 32;

    private HkdfKeyDeriver() {
    }

    static byte[] derive(byte[] rootKey, int keyVersion, String purpose) {
        byte[] info = (keyVersion + "|" + purpose).getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(new byte[HASH_LENGTH], ALGORITHM));
            byte[] pseudoRandomKey = mac.doFinal(rootKey);

            mac.init(new SecretKeySpec(pseudoRandomKey, ALGORITHM));
            mac.update(info);
            mac.update((byte) 0x01);
            byte[] derived = mac.doFinal();

            Arrays.fill(pseudoRandomKey, (byte) 0);
            return derived;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "Khong suy duoc khoa con HKDF-SHA256 cho muc dich " + purpose, ex);
        }
    }
}
