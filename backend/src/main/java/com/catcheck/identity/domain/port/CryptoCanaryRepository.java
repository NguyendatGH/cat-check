package com.catcheck.identity.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Luu vao {@code crypto_canary} (p4 §K3b) de kiem tra khoa ma hoa con dung sau
 * khi xoay. KHONG luu PII that — chi luu mot chuoi ngu nhien de so sanh round-trip.
 */
public interface CryptoCanaryRepository {

    void save(UUID id, String purpose, int keyVersion, byte[] cipherBlob, byte[] plaintextTag);

    /** Canary da co cho cap {@code (purpose, keyVersion)} hay chua. */
    boolean exists(String purpose, int keyVersion);

    void markVerified(UUID id);

    Optional<Canary> find(String purpose, int keyVersion);

    /**
     * @param cipherBlob    ket qua ma hoa theo bo cuc {@code [version][IV][ciphertext][tag]}
     * @param plaintextTag  16 byte goc de so sanh round-trip
     * @param verified      da duoc kiem tra thanh cong o lan khoi dong truoc do
     */
    record Canary(UUID id, byte[] cipherBlob, byte[] plaintextTag, boolean verified) {
    }
}
