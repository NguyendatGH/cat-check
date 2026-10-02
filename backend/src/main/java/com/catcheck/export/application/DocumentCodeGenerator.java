package com.catcheck.export.application;

import com.catcheck.export.domain.port.ExportJobRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Sinh mã tài liệu {@code CC-EXP-{năm}-{6 chữ số}} in ở chân trang PDF (p4 G1, p13 §13.6.2,
 * p8 §8.1.3). Thử lại tối đa vài lần nếu trùng {@code UNIQUE(document_code)} — không gian
 * 900.000 tổ hợp/năm đủ để va chạm gần như không xảy ra, nhưng vẫn kiểm tường minh.
 */
@Component
public class DocumentCodeGenerator {

    private static final int MAX_ATTEMPTS = 10;

    private final ExportJobRepository repository;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public DocumentCodeGenerator(ExportJobRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public String generate() {
        int year = clock.instant().atZone(ZoneOffset.UTC).getYear();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int suffix = 100000 + random.nextInt(900000);
            String code = "CC-EXP-" + year + "-" + suffix;
            if (!repository.existsByDocumentCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Không sinh được document_code duy nhất sau " + MAX_ATTEMPTS + " lần thử");
    }
}
