package com.catcheck.identity.infrastructure.crypto;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Khoa pepper co phien ban, dung cho HMAC ma OTP va ma kich hoat (p11 §11.2.1,
 * p11 §11.7.4).
 *
 * <p><b>Pepper khac khoa ma hoa PII va khac mat khau.</b> Pep-per nam ngoai DB nen
 * rot DB mot minh vo dung. No cung khac {@code BCrypt} cua mat khau: ma OTP chi 6
 * chu so nen dung HMAC chu khong dung ham bam cham de tranh bien endpoint xac thuc
 * thanh vector DoS (p11 §11.2.1).</p>
 *
 * <p><b>Khac nhau giua OTP va kich hoat</b> (p11 §11.7.4): ma kich hoat duoc gui qua
 * kenh khac, tao mot duong song song neu attacker lay duoc du lieu tu hai kenh do
 * thi khong ghep duoc cua hai loai ma. Vi vay khong dung chung mot pepper.</p>
 *
 * <p><b>Pepper khong luu ban ro.</b> Day la ly do ton tai {@code pepper_version} trong
 * {@code email_otp} (p11 §11.2.6): khi xoay pepper, ma dang bay se khong xac minh
 * duoc nua — chap nhan duoc, do TTL chi 5 phut.</p>
 */
final class PepperRing {

    /** 32 byte = 256 bit. It nhat vay de HMAC-SHA256 co nguon du nhieu. */
    static final int MIN_PEPPER_LENGTH = 32;

    private final SortedMap<Integer, byte[]> versions;
    private final int currentVersion;

    private PepperRing(SortedMap<Integer, byte[]> versions, int currentVersion) {
        this.versions = versions;
        this.currentVersion = currentVersion;
    }

    /**
     * @param rawSecret  gia tri bien moi truong (chuoi tho, >= {@link #MIN_PEPPER_LENGTH} byte)
     * @param rawVersion version hien tai; null thi mac dinh {@code 1}
     */
    static PepperRing of(String name, String rawSecret, String rawVersion) {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalStateException(
                    "Thieu bien moi truong " + name + ". App khong khoi dong duoc: khong ma hoa/Hash duoc "
                            + "ma OTP va ma khong con kiem soat so lan thu.");
        }
        byte[] secret = rawSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (secret.length < MIN_PEPPER_LENGTH) {
            throw new IllegalStateException(
                    name + " phai it nhat " + MIN_PEPPER_LENGTH + " byte, hien tai " + secret.length
                            + " byte. Pep-per ngan quang ninh thi mat bao ve tinh gia tri.");
        }
        int version = 1;
        if (rawVersion != null && !rawVersion.isBlank()) {
            try {
                version = Integer.parseInt(rawVersion.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalStateException(name + "_VERSION khong phai so: '" + rawVersion + "'", ex);
            }
            if (version < 1) {
                throw new IllegalStateException(name + "_VERSION phai >= 1");
            }
        }
        SortedMap<Integer, byte[]> map = new TreeMap<>();
        map.put(version, secret);
        return new PepperRing(map, version);
    }

    int currentVersion() {
        return currentVersion;
    }

    /** Nem neu version khong con trong ring — ma viet bang pepper da rut thi khong xac minh duoc. */
    byte[] keyFor(int version) {
        byte[] key = versions.get(version);
        if (key == null) {
            throw new IllegalStateException(
                    "Khong co pepper version " + version + " (dang co " + versions.keySet()
                            + "). Ma OTP viet bang version nay khong the xac minh.");
        }
        return key;
    }

    boolean knows(int version) {
        return versions.containsKey(version);
    }

    Map<Integer, Integer> knownVersions() {
        SortedMap<Integer, Integer> out = new TreeMap<>();
        versions.forEach((version, key) -> out.put(version, key.length));
        return out;
    }
}
