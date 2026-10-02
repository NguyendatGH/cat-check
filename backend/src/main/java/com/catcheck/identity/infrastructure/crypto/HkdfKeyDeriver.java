package com.catcheck.identity.infrastructure.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * HKDF-SHA256 (RFC 5869) — suy khoa con cho tung muc dich ma hoa.
 *
 * <p>JDK khong co API HKDF san nen cai dat tay. Toi thieu hoa: chi dung {@code extract}
 * + {@code expand} mot cap — {@code info} da du de tach khoa, khong can chuoi H hash dai
 * hon 32 byte (chi co 4 muc dich).</p>
 *
 * <p>Tach khoa bat buoc: {@code pii.phone} va {@code mfa.totp} phai la hai khoa AES
 * khac nhau du chung mot khoa goc, neu khong thi ma hoa so dien thoai co the dan duoc
 * tu khoa TOTP (va nguoc lai).</p>
 */
final class HkdfKeyDeriver {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int HASH_LENGTH = 32;

    private HkdfKeyDeriver() {
    }

    /**
     * @param rootKey khoa goc cho version hien tai (da giai base64 tu keyring)
     * @param salt    {@code keyVersion || purpose} — dung de tach version va muc dich
     * @return khoa con 32 byte cho AES-256
     */
    static byte[] derive(byte[] rootKey, int keyVersion, String purpose) {
        byte[] info = (keyVersion + "|" + purpose).getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            // Extract: PRK = HMAC(salt, IKM). Salt 32 byte zero la hop le
            // (khong phai secret, chi la nhan dang) va tom tat o day.
            mac.init(new SecretKeySpec(new byte[HASH_LENGTH], ALGORITHM));
            byte[] pseudoRandomKey = mac.doFinal(rootKey);

            // Expand: T(1) = HMAC(PRK, info || 0x01)
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
