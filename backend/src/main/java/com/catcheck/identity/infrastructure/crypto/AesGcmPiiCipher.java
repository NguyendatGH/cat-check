package com.catcheck.identity.infrastructure.crypto;

import com.catcheck.identity.api.CryptoPurpose;
import com.catcheck.identity.api.PiiCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

@Component
public class AesGcmPiiCipher implements PiiCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8;
    private static final int VERSION_LENGTH = 1;
    private static final int HEADER_LENGTH = VERSION_LENGTH + IV_LENGTH;

    private final PiiKeyring keyring;
    private final SecureRandom random = new SecureRandom();

    public AesGcmPiiCipher(
            @Value("${APP_PII_ENCRYPTION_KEYS:}") String rawKeys,
            @Value("${APP_PII_ENCRYPTION_WRITE_VERSION:}") String writeVersion) {
        Integer explicit = (writeVersion == null || writeVersion.isBlank())
                ? null
                : Integer.valueOf(writeVersion.trim());
        this.keyring = PiiKeyring.parse(rawKeys, explicit);
    }

    PiiKeyring keyring() {
        return keyring;
    }

    @Override
    public int currentWriteKeyVersion() {
        return keyring.writeVersion();
    }

    @Override
    public int writeKeyVersion() {
        return keyring.writeVersion();
    }

    @Override
    public byte[] encrypt(CryptoPurpose purpose, String aad, byte[] plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Khong ma hoa duoc null");
        }
        int version = keyring.writeVersion();
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(derivedKey(version, purpose), "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] body = cipher.doFinal(plaintext);

            byte[] out = new byte[HEADER_LENGTH + body.length];
            out[0] = (byte) version;
            System.arraycopy(iv, 0, out, VERSION_LENGTH, IV_LENGTH);
            System.arraycopy(body, 0, out, HEADER_LENGTH, body.length);
            return out;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Khong ma hoa duoc du lieu PII", ex);
        }
    }

    @Override
    public byte[] decrypt(CryptoPurpose purpose, String aad, byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length < HEADER_LENGTH + TAG_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "Ciphertext PII sai do dai (" + (ciphertext == null ? 0 : ciphertext.length)
                            + " byte) — du lieu bi hong hoac cot khong con duoc ma hoa");
        }
        int version = ciphertext[0] & 0xFF;
        byte[] key = derivedKey(version, purpose);
        byte[] iv = Arrays.copyOfRange(ciphertext, VERSION_LENGTH, HEADER_LENGTH);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            cipher.updateAAD(aad.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return cipher.doFinal(ciphertext, HEADER_LENGTH, ciphertext.length - HEADER_LENGTH);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "Khong giai ma duoc du lieu PII (key version " + version
                            + ", muc dich " + purpose.label() + ") — kiem tra khoa va AAD", ex);
        }
    }

    private byte[] derivedKey(int version, CryptoPurpose purpose) {
        byte[] rootKey = keyring.keyFor(version);
        return HkdfKeyDeriver.derive(rootKey, version, purpose.label());
    }
}
