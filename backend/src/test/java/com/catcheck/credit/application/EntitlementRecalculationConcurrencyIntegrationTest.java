package com.catcheck.credit.application;

import com.catcheck.credit.domain.CreditBatchGrant;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import com.catcheck.credit.infrastructure.persistence.JdbcCreditBatchAdapter;
import com.catcheck.credit.infrastructure.persistence.JdbcPackagePlanAdapter;
import com.catcheck.credit.infrastructure.persistence.JdbcUserEntitlementAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EntitlementRecalculationConcurrencyIntegrationTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/catcheck";
    private static final String DB_USER = "catcheck";
    private static final String DB_PASSWORD = "catcheck_local_only";

    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private CreditBatchPort batchPort;
    private UserEntitlementPort entitlementPort;
    private EntitlementRecalculationService recalculation;
    private PackagePlan plan;
    private UUID userId;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(JDBC_URL, DB_USER, DB_PASSWORD);
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        JsonMapper jsonMapper = JsonMapper.builder().build();
        JdbcPackagePlanAdapter planPort = new JdbcPackagePlanAdapter(jdbc, jsonMapper);
        batchPort = new JdbcCreditBatchAdapter(jdbc);
        entitlementPort = new JdbcUserEntitlementAdapter(jdbc, jsonMapper);
        recalculation = new EntitlementRecalculationService(entitlementPort, batchPort, planPort);
        plan = planPort.findByCode("MINI").orElseThrow();

        userId = jdbc.queryForObject("""
                INSERT INTO app_user (email, full_name, locale, timezone, status, email_verified_at)
                VALUES (?, 'Entitlement race test', 'vi', 'Asia/Ho_Chi_Minh', 'ACTIVE', now())
                RETURNING id
                """, UUID.class, "entitlement-race-" + UUID.randomUUID() + "@example.test");
        entitlementPort.ensureRow(userId, Instant.now());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM user_entitlement WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM credit_batch WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
    }

    @Test
    @DisplayName("Hai lan tinh lai dong thoi => write_access_until = hạn MUỘN NHẤT, không bị ghi đè lùi")
    void concurrentRecalculationsKeepLatestBatchExpiry() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant laterExpiry = now.plus(Duration.ofDays(30));
        Instant earlierExpiry = now.plus(Duration.ofDays(10));

        CountDownLatch firstRecalculated = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger secondPid = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> tx.executeWithoutResult(status -> {
                grant(laterExpiry, now);
                recalculation.recalculate(userId, now);
                firstRecalculated.countDown();
                awaitRelease(releaseFirst);
            }));
            assertThat(firstRecalculated.await(10, TimeUnit.SECONDS)).isTrue();

            Future<?> second = pool.submit(() -> tx.executeWithoutResult(status -> {
                secondPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                grant(earlierExpiry, now);
                recalculation.recalculate(userId, now);
            }));
            awaitBlockedOnLock(secondPid);
            releaseFirst.countDown();

            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
        }

        assertThat(entitlementPort.find(userId).orElseThrow().writeAccessUntil())
                .as("I28: write_access_until = MAX(expires_at) của mọi lô đã commit")
                .isEqualTo(laterExpiry);
    }

    private void grant(Instant expiresAt, Instant now) {
        batchPort.insertNewBatch(new CreditBatchGrant(
                UUID.randomUUID(), userId, null, plan.code(), plan.version(), 1, now, expiresAt));
    }

    private void awaitBlockedOnLock(AtomicInteger pid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (pid.get() != 0 && "Lock".equals(jdbc.query(
                    "SELECT wait_event_type FROM pg_stat_activity WHERE pid = ?",
                    rs -> rs.next() ? rs.getString(1) : null, pid.get()))) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("transaction thu hai khong cho khoa trong 10s");
    }

    private static void awaitRelease(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("het thoi gian cho");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
