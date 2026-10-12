package com.catcheck.credit.application;

import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeFormat;
import com.catcheck.credit.domain.ActivationCodeStatus;
import com.catcheck.credit.domain.port.ActivationCodeHasher;
import com.catcheck.credit.domain.port.ActivationCodePort;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ActivationCodeIssuanceService {

    private static final Logger log = LoggerFactory.getLogger(ActivationCodeIssuanceService.class);

    public static final int MAX_CODES_PER_BATCH = 50_000;

    private final ActivationCodePort codePort;
    private final PackagePlanPort packagePlanPort;
    private final ActivationCodeHasher codeHasher;
    private final SecureRandom secureRandom;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public ActivationCodeIssuanceService(
            ActivationCodePort codePort,
            PackagePlanPort packagePlanPort,
            ActivationCodeHasher codeHasher,
            SecureRandom secureRandom,
            UuidV7 uuidV7,
            Clock clock
    ) {
        this.codePort = codePort;
        this.packagePlanPort = packagePlanPort;
        this.codeHasher = codeHasher;
        this.secureRandom = secureRandom;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    @Transactional
    public IssuedBatch issue(String packageCode, int quantity, String productionBatch, int validForDays) {
        if (quantity < 1 || quantity > MAX_CODES_PER_BATCH) {
            throw new IllegalArgumentException(
                    "Số mã phải nằm trong [1, " + MAX_CODES_PER_BATCH + "], nhận: " + quantity);
        }
        if (validForDays < 1) {
            throw new IllegalArgumentException("Hạn kích hoạt phải >= 1 ngày, nhận: " + validForDays);
        }
        if (packagePlanPort.findByCode(packageCode).isEmpty()) {
            throw new NoSuchElementException("Không có gói " + packageCode + " trong package_plan");
        }

        Instant issuedAt = clock.instant();
        Instant validUntil = issuedAt.plus(validForDays, ChronoUnit.DAYS);
        String codePrefix = ActivationCodeFormat.codePrefixOf(packageCode);

        java.util.Set<String> hashes = new java.util.HashSet<>(quantity * 2);
        java.util.List<String> rawCodes = new java.util.ArrayList<>(quantity);
        java.util.List<ActivationCode> rows = new java.util.ArrayList<>(quantity);
        while (rows.size() < quantity) {
            String raw = ActivationCodeFormat.generate(packageCode, secureRandom);
            ActivationCodeHasher.HashedActivationCode hashed = codeHasher.hash(raw);
            if (!hashes.add(hashed.hex())) {
                continue;
            }
            rows.add(new ActivationCode(
                    uuidV7.generate(), hashed.hex(), hashed.pepperVersion(), codePrefix, packageCode,
                    productionBatch, issuedAt, validUntil, ActivationCodeStatus.ISSUED, null, null));
            rawCodes.add(raw);
        }
        codePort.insertAll(rows);

        log.info("Phát hành {} mã gói {}, lô sản xuất {}", quantity, packageCode, productionBatch);

        return new IssuedBatch(packageCode, productionBatch, issuedAt, validUntil, List.copyOf(rawCodes));
    }

    public record IssuedBatch(
            String packageCode,
            String productionBatch,
            Instant issuedAt,
            Instant validUntil,
            List<String> rawCodes
    ) {

        public IssuedBatch {
            rawCodes = rawCodes == null ? List.of() : List.copyOf(rawCodes);
        }
    }
}
