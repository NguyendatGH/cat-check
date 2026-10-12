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

@Component
public class CryptoCanaryBootstrap {

    private static final Logger log = LoggerFactory.getLogger(CryptoCanaryBootstrap.class);

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
