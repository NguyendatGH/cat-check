package com.catcheck.identity.api;

import java.util.UUID;

public interface PiiCipher {

    byte[] encrypt(CryptoPurpose purpose, String aad, byte[] plaintext);

    byte[] decrypt(CryptoPurpose purpose, String aad, byte[] ciphertext);

    int currentWriteKeyVersion();

    int writeKeyVersion();

    static String aad(String table, String column, UUID rowId) {
        return table + "|" + column + "|" + rowId;
    }
}
