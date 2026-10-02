package com.catcheck.identity.infrastructure.crypto;

import com.catcheck.identity.api.CryptoPurpose;
import com.catcheck.identity.api.PiiCipher;
import com.catcheck.identity.domain.port.CryptoCanaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * Kiem tra ma hoa PII luc khoi dong, fail-fast (p11 §11.10.3, p4 §K3b).
 *
 * <p>Voi MOI muc dich {@link CryptoPurpose} va MOI version khoa trong keyring:</p>
 * <ol>
 *   <li>Lan dau: ghi mot canary — 16 byte ngau nhien, ma hoa roi ghi xuong
 *       {@code crypto_canary}. KHONG luu PII that, chi luu chuoi ngu nhien de
 *       so sanh round-trip.</li>
 *   <li>Nhung lan sau: doc canary cu, giai ma, so sanh byte-for-byte. Lech mot
 *       byte = app <b>khong khoi dong</b>.</li>
 * </ol>
 *
 * <p>Vi sao can canary thay vi chi giai thu mot chuoi sinh ngay trong ram: khoa
 * bi doi/rot sau khi app da ghi du lieu se khong bi phat hien neu khong co du lieu
 * da ghi. Canary la bang chung la du lieu cu con doc duoc.</p>
 *
 * <p>AAD cua canary dung dung quy uoc that cua PII
 * ({@code crypto_canary|cipher_blob|<id>}) de kiem tra ca duong chua khoa con HKDF.</p>
 */
@Component
public class CryptoCanaryBootstrap {

    private static final Logger log = LoggerFactory.getLogger(CryptoCanaryBootstrap.class);

    /** 16 byte — bang do dai tag luu trong DB (CHECK {@code length(plaintext_tag) = 16}). */
    private static final int CANARY_TAG_LENGTH = 16;

    private static final String CANARY_TABLE = "crypto_canary";
    private static final String CANARY_COLUMN = "cipher_blob";

    private final PiiCipher cipher;
    private final PiiKeyring keyring;
    private final CryptoCanaryRepository canaries;
    private final SecureRandom random = new SecureRandom();

    public CryptoCanaryBootstrap(PiiCipher cipher, AesGcmPiiCipher rawCipher,
                                 CryptoCanaryRepository canaries) {
        this.cipher = cipher;
        // Doc keyring tu chinh bo ma hoa de biet het cac version dang duoc cau hinh,
        // thay vi tach keyring thanh mot bean public (khoa goc phai duoc giu private).
        this.keyring = rawCipher.keyring();
        this.canaries = canaries;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyAllPurposes() {
        Map<Integer, Integer> versions = keyring.knownVersions();
        log.info("Kiem tra canary ma hoa PII: {} version khoa, {} muc dich",
                versions.size(), CryptoPurpose.values().length);

        for (int version : versions.keySet()) {
            for (CryptoPurpose purpose : CryptoPurpose.values()) {
                verify(purpose, version);
            }
        }
    }

    private void verify(CryptoPurpose purpose, int version) {
        String label = purpose.label() + "@v" + version;
        var existing = canaries.find(purpose.label(), version);

        if (existing.isPresent()) {
            CryptoCanaryRepository.Canary canary = existing.get();
            byte[] decrypted = cipher.decrypt(purpose, aadFor(canary.id()), canary.cipherBlob());
            if (!Arrays.equals(decrypted, canary.plaintextTag())) {
                throw new IllegalStateException(
                        ("Canary ma hoa PII KHONG khop cho %s. App khong khoi dong: du lieu da ghi "
                                + "bang cap khoa nay khong giai ma duoc (khoa bi doi, bi rut, hoac AAD sai). "
                                + "Chi xoa canary khi da xac nhan nguyen nhan la cau hinh, khong phai mat du lieu.")
                                .formatted(label));
            }
            canaries.markVerified(canary.id());
            log.debug("Canary {} giai ma khop.", label);
            return;
        }

        UUID id = UUID.randomUUID();
        byte[] tag = new byte[CANARY_TAG_LENGTH];
        random.nextBytes(tag);
        byte[] blob = cipher.encrypt(purpose, aadFor(id), tag);

        // Giai lai ngay de bat loi o lan ghi dau tien chu khong phai lan khoi dong sau.
        byte[] roundTrip = cipher.decrypt(purpose, aadFor(id), blob);
        if (!Arrays.equals(roundTrip, tag)) {
            throw new IllegalStateException(
                    "Canary ma hoa PII that bai khi vong tron cho " + label + ". App khong khoi dong.");
        }
        canaries.save(id, purpose.label(), version, blob, tag);
        log.info("Da tao canary ma hoa PII moi cho {}.", label);
    }

    private String aadFor(UUID rowId) {
        return PiiCipher.aad(CANARY_TABLE, CANARY_COLUMN, rowId);
    }
}
