package com.catcheck.shared.id;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Sinh UUID version 7 (RFC 9562 §5.7) — timestamp-ordered, dùng làm khoá chính cho entity mới
 * từ M1+ (đọc thêm p4). Cài đặt thuần Java (không phụ thuộc thư viện ngoài) vì chưa xác nhận
 * được Hibernate 7.4.5/Boot 4.1 có generator UUIDv7 chuẩn sẵn hay không trong phạm vi bản trích
 * spec cho nhiệm vụ này.
 *
 * <p>Bố cục 128 bit:</p>
 * <pre>
 * 48 bit  unix_ts_ms (big-endian)
 *  4 bit  version = 0111
 * 12 bit  rand_a
 *  2 bit  variant = 10
 * 62 bit  rand_b
 * </pre>
 *
 * <p>Là {@code @Component} nhận {@link Clock} qua constructor (KHÔNG gọi
 * {@code Instant.now()} trực tiếp — tuân thủ ArchUnit R13) thay vì một static util gọi thẳng
 * đồng hồ hệ thống. Dùng ở tầng application: sinh id tường minh trước khi tạo entity, thay vì
 * trong {@code @PrePersist} (entity JPA không phải Spring bean nên không inject được).</p>
 */
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

        // 6 byte đầu: unix epoch millisecond, big-endian.
        uuidBytes[0] = (byte) (epochMilli >>> 40);
        uuidBytes[1] = (byte) (epochMilli >>> 32);
        uuidBytes[2] = (byte) (epochMilli >>> 24);
        uuidBytes[3] = (byte) (epochMilli >>> 16);
        uuidBytes[4] = (byte) (epochMilli >>> 8);
        uuidBytes[5] = (byte) epochMilli;

        // 10 byte còn lại: ngẫu nhiên (chứa cả rand_a lẫn rand_b, sẽ bị ghi đè một phần bit).
        byte[] randomBytes = new byte[10];
        random.nextBytes(randomBytes);
        System.arraycopy(randomBytes, 0, uuidBytes, 6, 10);

        // version = 0111 ở 4 bit cao của byte 6.
        uuidBytes[6] = (byte) ((uuidBytes[6] & 0x0F) | 0x70);
        // variant = 10 ở 2 bit cao của byte 8.
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
