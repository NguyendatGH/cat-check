package com.catcheck.identity.infrastructure.crypto;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Doc bien moi truong {@code APP_PII_ENCRYPTION_KEYS} va
 * {@code APP_PII_ENCRYPTION_WRITE_VERSION} (p11 §11.10.3).
 *
 * <p>Dinh dang: {@code v1:<base64>,v2:<base64>} — <b>phai co dau {@code v}</b>. Khoa dau
 * tien chi dung de ghi, cac khoa con lai chi dung de doc du lieu cu. Version ghi se
 * dung la <b>version dau tien</b> trong danh sach; bien
 * {@code APP_PII_ENCRYPTION_WRITE_VERSION} cho phep tro khi danh sach duoc sap xep lai.</p>
 *
 * <p>Fail-fast trong constructor: app khong khoi dong duoc neu thieu bien hoac khoa
 * khong dung 32 byte. Day la chu dich — mot app chay nhe ma khong ma hoa PII se ghi
 * du lieu tho vao DB ma khong ai phat hien.</p>
 */
final class PiiKeyring {

    static final int KEY_LENGTH = 32;

    private final SortedMap<Integer, byte[]> keysByVersion;
    private final int writeVersion;

    private PiiKeyring(SortedMap<Integer, byte[]> keysByVersion, int writeVersion) {
        this.keysByVersion = keysByVersion;
        this.writeVersion = writeVersion;
    }

    static PiiKeyring parse(String rawKeys, Integer explicitWriteVersion) {
        if (rawKeys == null || rawKeys.isBlank()) {
            throw new IllegalStateException(
                    "Thieu bien moi truong APP_PII_ENCRYPTION_KEYS. App khong khoi dong duoc "
                            + "vi khong ma hoa duoc PII (xem p11 §11.10.3).");
        }
        SortedMap<Integer, byte[]> keys = new TreeMap<>();
        for (String entry : rawKeys.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator < 0) {
                throw new IllegalStateException(
                        "APP_PII_ENCRYPTION_KEYS sai dinh dang, thieu dau ':' trong muc '" + trimmed + "'");
            }
            String versionPart = trimmed.substring(0, separator).trim();
            if (!versionPart.startsWith("v") || versionPart.length() < 2) {
                throw new IllegalStateException(
                        "Version khoa phai co dang 'v1', 'v2'... nhung nhan duoc '" + versionPart + "'");
            }
            int version;
            try {
                version = Integer.parseInt(versionPart.substring(1));
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("Version khoa khong phai so: '" + versionPart + "'", ex);
            }
            byte[] key = decodeBase64(trimmed.substring(separator + 1).trim(), versionPart);
            if (key.length != KEY_LENGTH) {
                throw new IllegalStateException(
                        "Khoa " + versionPart + " phai dai dung " + KEY_LENGTH + " byte cho AES-256, dang co "
                                + key.length);
            }
            if (keys.putIfAbsent(version, key) != null) {
                throw new IllegalStateException("APP_PII_ENCRYPTION_KEYS lap lai version " + versionPart);
            }
        }
        if (keys.isEmpty()) {
            throw new IllegalStateException("APP_PII_ENCRYPTION_KEYS khong chua khoa nao");
        }

        int write = keys.firstKey();
        if (explicitWriteVersion != null) {
            if (!keys.containsKey(explicitWriteVersion)) {
                throw new IllegalStateException(
                        "APP_PII_ENCRYPTION_WRITE_VERSION=" + explicitWriteVersion
                                + " khong co trong APP_PII_ENCRYPTION_KEYS (chi co " + keys.keySet() + ")");
            }
            write = explicitWriteVersion;
        }
        return new PiiKeyring(keys, write);
    }

    private static byte[] decodeBase64(String value, String versionPart) {
        try {
            return java.util.Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Khoa " + versionPart + " khong phai Base64 hop le", ex);
        }
    }

    int writeVersion() {
        return writeVersion;
    }

    /** Khu vuc cho version, nem neu version khong con trong keyring. */
    byte[] keyFor(int version) {
        byte[] key = keysByVersion.get(version);
        if (key == null) {
            // KHONG fallback ve version dau tien: du lieu duoc ghi bang khoa da rut
            // thi phai bao loi, chiu khong phai tra ve rong va ghi de bien mat nham.
            throw new IllegalStateException(
                    "Khong co khoa version " + version + " trong APP_PII_ENCRYPTION_KEYS "
                            + "(dang co " + keysByVersion.keySet() + "). "
                            + "Du lieu duoc ma hoa bang khoa nay khong giai ma duoc.");
        }
        return key;
    }

    Map<Integer, Integer> knownVersions() {
        SortedMap<Integer, Integer> out = new TreeMap<>();
        keysByVersion.forEach((version, key) -> out.put(version, key.length));
        return out;
    }
}
