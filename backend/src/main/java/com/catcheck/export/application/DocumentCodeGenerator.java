package com.catcheck.export.application;

import com.catcheck.export.domain.port.ExportJobRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;

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
