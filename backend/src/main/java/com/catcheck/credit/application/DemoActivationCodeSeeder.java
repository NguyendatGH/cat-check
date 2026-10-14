package com.catcheck.credit.application;

import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeFormat;
import com.catcheck.credit.domain.ActivationCodeStatus;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.port.ActivationCodeHasher;
import com.catcheck.credit.domain.port.ActivationCodePort;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Chỉ chạy ở profile {@code local}: gieo một bộ mã kích hoạt CỐ ĐỊNH (mã thô biết trước, liệt kê ở
 * docs/DEMO_ACCOUNTS.md) để demo luồng nạp credit. Idempotent: mã nào đã có {@code code_hash} trong DB
 * thì bỏ qua, không bao giờ sửa/reset mã đã REDEEMED hay VOID.
 */
@Component
@Profile("local")
public class DemoActivationCodeSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoActivationCodeSeeder.class);

    static final String DEMO_BATCH = "DEMO";
    static final String DEMO_EXPIRED_BATCH = "DEMO-EXPIRED";
    static final int VALID_FOR_DAYS = 365;
    static final int CODES_PER_PACKAGE = 5;

    /** Gói theo db/seed/R__seed_package_plan.sql; gói không tồn tại/không active thì bỏ qua. */
    static final List<String> DEMO_PACKAGES = List.of("MINI", "DAILY", "PLUS", "MULTI", "CARE_BOX");

    static final String EXPIRED_PACKAGE = "PLUS";
    static final String EXPIRED_RANDOM_PART = "EXP000001";

    record DemoCode(String rawCode, String packageCode, String productionBatch, boolean expired) { }

    private final ActivationCodePort codePort;
    private final PackagePlanPort packagePlanPort;
    private final ActivationCodeHasher codeHasher;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public DemoActivationCodeSeeder(ActivationCodePort codePort,
                                    PackagePlanPort packagePlanPort,
                                    ActivationCodeHasher codeHasher,
                                    UuidV7 uuidV7,
                                    Clock clock) {
        this.codePort = codePort;
        this.packagePlanPort = packagePlanPort;
        this.codeHasher = codeHasher;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /** Danh sách mã demo cố định: CC-&lt;GÓI&gt;-DEM0000NN + checksum, cộng một mã PLUS đã hết hạn. */
    static List<DemoCode> demoCodes() {
        List<DemoCode> codes = new ArrayList<>();
        for (String packageCode : DEMO_PACKAGES) {
            for (int n = 1; n <= CODES_PER_PACKAGE; n++) {
                String randomPart = "DEM0000" + String.format(java.util.Locale.ROOT, "%02d", n);
                codes.add(new DemoCode(ActivationCodeFormat.compose(packageCode, randomPart),
                        packageCode, DEMO_BATCH, false));
            }
        }
        codes.add(new DemoCode(ActivationCodeFormat.compose(EXPIRED_PACKAGE, EXPIRED_RANDOM_PART),
                EXPIRED_PACKAGE, DEMO_EXPIRED_BATCH, true));
        return List.copyOf(codes);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    @Transactional
    public int seed() {
        Instant now = clock.instant();
        int inserted = 0;
        for (DemoCode demo : demoCodes()) {
            Optional<PackagePlan> plan = packagePlanPort.findByCode(demo.packageCode());
            if (plan.isEmpty() || !plan.get().active()) {
                continue;
            }
            ActivationCodeHasher.HashedActivationCode hashed = codeHasher.hash(demo.rawCode());
            if (codePort.findByCodeHash(hashed.hex()).isPresent()) {
                continue;
            }
            Instant issuedAt = demo.expired() ? now.minus(VALID_FOR_DAYS + 1L, ChronoUnit.DAYS) : now;
            Instant validUntil = demo.expired()
                    ? now.minus(1, ChronoUnit.DAYS)
                    : now.plus(VALID_FOR_DAYS, ChronoUnit.DAYS);
            codePort.insert(new ActivationCode(
                    uuidV7.generate(), hashed.hex(), hashed.pepperVersion(),
                    ActivationCodeFormat.codePrefixOf(demo.packageCode()), demo.packageCode(),
                    demo.productionBatch(), issuedAt, validUntil, ActivationCodeStatus.ISSUED, null, null));
            inserted++;
        }
        if (inserted > 0) {
            log.info("Gieo {} mã kích hoạt demo (profile local)", inserted);
        }
        return inserted;
    }
}
