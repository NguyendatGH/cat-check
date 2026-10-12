package com.catcheck.identity.infrastructure.crypto;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

final class PepperRing {

    static final int MIN_PEPPER_LENGTH = 32;

    private final SortedMap<Integer, byte[]> versions;
    private final int currentVersion;

    private PepperRing(SortedMap<Integer, byte[]> versions, int currentVersion) {
        this.versions = versions;
        this.currentVersion = currentVersion;
    }

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
