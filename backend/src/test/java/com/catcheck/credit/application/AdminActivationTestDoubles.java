package com.catcheck.credit.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.credit.domain.ActivationBatchSummary;
import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeFilter;
import com.catcheck.credit.domain.ActivationCodeStatus;
import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.HistoryLevel;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PackagePlanAdminView;
import com.catcheck.credit.domain.PackagePlanUpdate;
import com.catcheck.credit.domain.PlanFeatures;
import com.catcheck.credit.domain.ActivationRateLimitRule;
import com.catcheck.credit.domain.port.ActivationCodeHasher;
import com.catcheck.credit.domain.port.ActivationCodePort;
import com.catcheck.credit.domain.port.ActivationRateLimiter;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class AdminActivationTestDoubles {

    private AdminActivationTestDoubles() {
    }

    static final class InMemoryActivationCodePort implements ActivationCodePort {

        final Map<UUID, ActivationCode> rows = new HashMap<>();
        final List<String> lockedProductionBatches = new ArrayList<>();

        @Override
        public Optional<ActivationCode> findByCodeHash(String codeHash) {
            return rows.values().stream().filter(row -> row.codeHash().equals(codeHash)).findFirst();
        }

        @Override
        public void insert(ActivationCode code) {
            if (findByCodeHash(code.codeHash()).isPresent()) {
                throw new IllegalStateException("trùng code_hash: " + code.id());
            }
            rows.put(code.id(), code);
        }

        @Override
        public void insertAll(List<ActivationCode> codes) {
            codes.forEach(this::insert);
        }

        @Override
        public boolean markRedeemed(UUID codeId, UUID userId, Instant redeemedAt) {
            ActivationCode code = rows.get(codeId);
            if (code == null || code.status() != ActivationCodeStatus.ISSUED) {
                return false;
            }
            rows.put(codeId, withStatus(code, ActivationCodeStatus.REDEEMED, userId, redeemedAt));
            return true;
        }

        @Override
        public boolean markVoid(UUID codeId) {
            ActivationCode code = rows.get(codeId);
            if (code == null || code.status() != ActivationCodeStatus.ISSUED) {
                return false;
            }
            rows.put(codeId, withStatus(code, ActivationCodeStatus.VOID, null, null));
            return true;
        }

        @Override
        public List<ActivationCode> findIssuedCodes(
                String packageCode, ActivationCodeStatus status, int offset, int limit) {
            return rows.values().stream()
                    .filter(row -> row.packageCode().equals(packageCode) && row.status() == status)
                    .skip(offset).limit(limit).toList();
        }

        @Override
        public long countIssuedCodes(String packageCode, ActivationCodeStatus status) {
            return rows.values().stream()
                    .filter(row -> row.packageCode().equals(packageCode) && row.status() == status)
                    .count();
        }

        @Override
        public Optional<ActivationCode> findById(UUID codeId) {
            return Optional.ofNullable(rows.get(codeId));
        }

        @Override
        public List<ActivationCode> search(ActivationCodeFilter filter, int offset, int limit) {
            return matching(filter).skip(offset).limit(limit).toList();
        }

        @Override
        public long count(ActivationCodeFilter filter) {
            return matching(filter).count();
        }

        @Override
        public List<ActivationBatchSummary> listBatches(int offset, int limit) {
            return rows.values().stream()
                    .map(ActivationCode::productionBatch)
                    .filter(batch -> batch != null)
                    .distinct()
                    .sorted()
                    .skip(offset).limit(limit)
                    .map(batch -> findBatch(batch).orElseThrow())
                    .toList();
        }

        @Override
        public long countBatches() {
            return rows.values().stream()
                    .map(ActivationCode::productionBatch)
                    .filter(batch -> batch != null)
                    .distinct().count();
        }

        @Override
        public Optional<ActivationBatchSummary> findBatch(String productionBatch) {
            List<ActivationCode> inBatch = rows.values().stream()
                    .filter(row -> productionBatch.equals(row.productionBatch()))
                    .sorted(Comparator.comparing(ActivationCode::issuedAt))
                    .toList();
            if (inBatch.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ActivationBatchSummary(
                    productionBatch,
                    inBatch.getFirst().packageCode(),
                    inBatch.size(),
                    countStatus(inBatch, ActivationCodeStatus.ISSUED),
                    countStatus(inBatch, ActivationCodeStatus.REDEEMED),
                    countStatus(inBatch, ActivationCodeStatus.VOID),
                    inBatch.getFirst().issuedAt(),
                    inBatch.getFirst().validUntil()));
        }

        @Override
        public void lockProductionBatch(String productionBatch) {
            lockedProductionBatches.add(productionBatch);
        }

        @Override
        public int markBatchVoid(String productionBatch) {
            List<UUID> ids = rows.values().stream()
                    .filter(row -> productionBatch.equals(row.productionBatch())
                            && row.status() == ActivationCodeStatus.ISSUED)
                    .map(ActivationCode::id)
                    .toList();
            ids.forEach(this::markVoid);
            return ids.size();
        }

        @Override
        public Optional<ActivationCode> lockById(UUID codeId) {
            return findById(codeId);
        }

        private java.util.stream.Stream<ActivationCode> matching(ActivationCodeFilter filter) {
            return rows.values().stream()
                    .filter(row -> filter.codePrefix() == null
                            || row.codePrefix().startsWith(filter.codePrefix()))
                    .filter(row -> filter.packageCode() == null
                            || filter.packageCode().equals(row.packageCode()))
                    .filter(row -> filter.status() == null || filter.status() == row.status())
                    .filter(row -> filter.productionBatch() == null
                            || filter.productionBatch().equals(row.productionBatch()))
                    .sorted(Comparator.comparing(ActivationCode::issuedAt)
                            .thenComparing(ActivationCode::id));
        }

        private static long countStatus(List<ActivationCode> codes, ActivationCodeStatus status) {
            return codes.stream().filter(code -> code.status() == status).count();
        }

        private static ActivationCode withStatus(ActivationCode code, ActivationCodeStatus status,
                                                 UUID redeemedBy, Instant redeemedAt) {
            return new ActivationCode(code.id(), code.codeHash(), code.pepperVersion(),
                    code.codePrefix(), code.packageCode(), code.productionBatch(), code.issuedAt(),
                    code.validUntil(), status, redeemedBy, redeemedAt);
        }
    }

    static final class FixedPackagePlanPort implements PackagePlanPort {

        private final Map<String, PackagePlan> plans = new HashMap<>();
        Instant updatedAt = Instant.parse("2026-01-01T00:00:00Z");

        FixedPackagePlanPort(String code, int creditAmount, int validityDays) {
            plans.put(code, new PackagePlan(code, "Gói " + code, new BigDecimal("1.50"),
                    creditAmount, validityDays, 1,
                    new PlanFeatures(HistoryLevel.BASIC, true, false, false, false), true, 1));
        }

        @Override
        public Optional<PackagePlan> findByCode(String code) {
            return Optional.ofNullable(plans.get(code));
        }

        @Override
        public List<PackagePlan> findAllActive() {
            return List.copyOf(plans.values());
        }

        @Override
        public List<PackagePlanAdminView> findAllForAdmin(boolean includeInactive) {
            return plans.values().stream()
                    .filter(plan -> includeInactive || plan.active())
                    .map(plan -> new PackagePlanAdminView(plan, updatedAt))
                    .toList();
        }

        @Override
        public Optional<PackagePlanAdminView> findForAdmin(String code) {
            return findByCode(code).map(plan -> new PackagePlanAdminView(plan, updatedAt));
        }

        @Override
        public boolean update(String code, PackagePlanUpdate update) {
            PackagePlan current = plans.get(code);
            if (current == null || update.isEmpty()) {
                return false;
            }
            plans.put(code, new PackagePlan(
                    current.code(), current.name(), current.weightKg(),
                    update.creditAmount() == null ? current.creditAmount() : update.creditAmount(),
                    update.creditValidityDays() == null
                            ? current.creditValidityDays() : update.creditValidityDays(),
                    update.clearMaxCatProfiles() ? null
                            : (update.maxCatProfiles() == null
                            ? current.maxCatProfiles() : update.maxCatProfiles()),
                    update.features() == null ? current.features() : update.features(),
                    update.active() == null ? current.active() : update.active(),
                    current.version() + 1));
            return true;
        }
    }

    static final class TestCodeHasher implements ActivationCodeHasher {

        @Override
        public HashedActivationCode hash(String normalizedCode) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update("test-pepper".getBytes(StandardCharsets.UTF_8));
                byte[] bytes = digest.digest(normalizedCode.getBytes(StandardCharsets.UTF_8));
                return new HashedActivationCode(HexFormat.of().formatHex(bytes), (short) 1);
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    static final class RecordingAuditLog implements AuditLogService {

        final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void record(AuditEvent event) {
            events.add(event);
        }

        List<String> actions() {
            return events.stream().map(AuditEvent::action).toList();
        }
    }

    static final class AllowAllRateLimiter implements ActivationRateLimiter {

        @Override
        public boolean tryConsume(ActivationRateLimitRule rule, String subject) {
            return true;
        }

        @Override
        public long secondsUntilRefill(ActivationRateLimitRule rule, String subject) {
            return 0;
        }
    }

    static final class FakeEntitlementPort implements UserEntitlementPort {

        final Map<UUID, Entitlement> rows = new HashMap<>();

        @Override
        public Optional<Entitlement> find(UUID userId) {
            return Optional.ofNullable(rows.get(userId));
        }

        @Override
        public Entitlement findOrDefault(UUID userId, Instant now) {
            return rows.getOrDefault(userId, Entitlement.defaults(userId, now));
        }

        @Override
        public void ensureRow(UUID userId, Instant now) {
            rows.putIfAbsent(userId, Entitlement.defaults(userId, now));
        }

        @Override
        public void lockRow(UUID userId, Instant now) {
            ensureRow(userId, now);
        }

        @Override
        public void save(Entitlement entitlement) {
            rows.put(entitlement.userId(), entitlement);
        }

        @Override
        public boolean incrementTrialScansUsed(UUID userId, int trialScanLimit, Instant now) {
            return false;
        }

        @Override
        public Optional<Instant> maxActivatedBatchExpiry(UUID userId) {
            return Optional.empty();
        }
    }
}
