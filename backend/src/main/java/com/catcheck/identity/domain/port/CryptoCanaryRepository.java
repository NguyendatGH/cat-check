package com.catcheck.identity.domain.port;

import java.util.Optional;
import java.util.UUID;

public interface CryptoCanaryRepository {

    void save(UUID id, String purpose, int keyVersion, byte[] cipherBlob, byte[] plaintextTag);

    boolean exists(String purpose, int keyVersion);

    void markVerified(UUID id);

    Optional<Canary> find(String purpose, int keyVersion);

    record Canary(UUID id, byte[] cipherBlob, byte[] plaintextTag, boolean verified) {
    }
}
