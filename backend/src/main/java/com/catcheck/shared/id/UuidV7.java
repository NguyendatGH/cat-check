package com.catcheck.shared.id;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

@Component
public class UuidV7 {

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public UuidV7(Clock clock) {
        this.clock = clock;
    }

    public UUID generate() {
        return fromEpochMilli(clock.millis(), random);
    }

    static UUID fromEpochMilli(long epochMilli, SecureRandom random) {
        byte[] uuidBytes = new byte[16];

        uuidBytes[0] = (byte) (epochMilli >>> 40);
        uuidBytes[1] = (byte) (epochMilli >>> 32);
        uuidBytes[2] = (byte) (epochMilli >>> 24);
        uuidBytes[3] = (byte) (epochMilli >>> 16);
        uuidBytes[4] = (byte) (epochMilli >>> 8);
        uuidBytes[5] = (byte) epochMilli;

        byte[] randomBytes = new byte[10];
        random.nextBytes(randomBytes);
        System.arraycopy(randomBytes, 0, uuidBytes, 6, 10);

        uuidBytes[6] = (byte) ((uuidBytes[6] & 0x0F) | 0x70);
        uuidBytes[8] = (byte) ((uuidBytes[8] & 0x3F) | 0x80);

        long msb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (uuidBytes[i] & 0xFF);
        }
        long lsb = 0;
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (uuidBytes[i] & 0xFF);
        }

        return new UUID(msb, lsb);
    }
}
