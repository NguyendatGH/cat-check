package com.catcheck.identity.api;

import java.util.UUID;

/**
 * <b>Cong ma hoa PII dung chung cho moi module</b> — phai nam o {@code identity/api/}
 * de module khac goi duoc ma khong phai phu thuoc vao {@code identity.infrastructure}
 * (R3: {@code ..api..} khong duoc phu thuoc {@code ..infrastructure..}).
 *
 * <p>Module khac lien quan se goi qua day, vi du {@code notification} khi ma hoa
 * {@code push_token}: {@code notification} KHONG duoc import
 * {@code identity.infrastructure.crypto.AesGcmPiiCipher}, ma chi import
 * {@code com.catcheck.identity.api.PiiCipher} qua named interface {@code identity::api}.</p>
 *
 * <p>Thuật toan: AES-256-GCM, IV 12 byte ngau nhien cho moi lan ghi, AAD bat buoc la
 * {@code "<table>|<column>|<row_id>"} (p11 §11.10.3). Bo cuc ket qua:
 * {@code [1 byte key_version][12 byte IV][ciphertext][16 byte tag]}.</p>
 *
 * <p>Bat buon cho {@code keyVersion} khi giai ma (p11 §11.10.3: phai luu
 * {@code phone_key_version} de xoay khoa — khoa dau tien trong danh sach chi dung
 * de ghi, cac khoa con lai chi de doc du lieu cu).</p>
 */
public interface PiiCipher {

    /**
     * @param purpose muc dich, tach khoa con qua HKDF-SHA256 — xem {@link CryptoPurpose}
     * @param aad     {@code "<table>|<column>|<row_id>"}; {@code rowId} la UUID cua dong
     *                chua so lieu (co the sinh truoc khi insert)
     * @return ciphertext kem version va IV nhung lai
     */
    byte[] encrypt(CryptoPurpose purpose, String aad, byte[] plaintext);

    /**
     * @throws com.catcheck.identity.application.CryptoUnavailableException neu ciphertext
     *         sai dinh dang, sai AAD, hoac khoa khong con trong keyring (nghia la du lieu
     *         duoc ghi bang khoa da bi rut — phai bao loi, KHONG duoc fallback "tra ve rong")
     */
    byte[] decrypt(CryptoPurpose purpose, String aad, byte[] ciphertext);

    /** Lay version khoa da dung de ghi, de luu vao cot {@code *_key_version}. */
    int currentWriteKeyVersion();

    /** Version nao dang duoc dung de ghi — doc tu bien moi truong, khong phai "cai dau tien". */
    int writeKeyVersion();

    /** Tao AAD chuan de moi noi dung cung mot quy uoc (tranh goi nham). */
    static String aad(String table, String column, UUID rowId) {
        return table + "|" + column + "|" + rowId;
    }
}
