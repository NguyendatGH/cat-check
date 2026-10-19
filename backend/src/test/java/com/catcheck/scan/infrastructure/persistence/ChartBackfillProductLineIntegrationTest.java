package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.application.ChartBackfillService;
import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.color.DeltaE2000Params;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;
import com.catcheck.scan.domain.color.PhChartPoint;
import com.catcheck.shared.id.UuidV7;
import com.catcheck.shared.job.JobContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChartBackfillProductLineIntegrationTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/catcheck";
    private static final String DB_USER = "catcheck";
    private static final String DB_PASSWORD = "catcheck_local_only";

    private JdbcTemplate jdbc;
    private EntityManagerFactory emf;
    private TransactionTemplate tx;
    private ChartBackfillService service;

    private UUID userId;
    private UUID targetChartId;
    private UUID standardChartId;
    private UUID miniChartId;
    private UUID otherBatchChartId;
    private UUID sameLineScan;
    private UUID otherLineScan;
    private UUID otherBatchScan;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(JDBC_URL, DB_USER, DB_PASSWORD);
        jdbc = new JdbcTemplate(dataSource);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.catcheck.scan.domain");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.jdbc.time_zone", "UTC"));
        factory.afterPropertiesSet();
        emf = factory.getObject();
        JpaTransactionManager transactionManager = new JpaTransactionManager(emf);
        tx = new TransactionTemplate(transactionManager);

        EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(emf);
        JpaRepositoryFactory repositories = new JpaRepositoryFactory(em);
        Clock clock = Clock.systemUTC();
        service = new ChartBackfillService(
                new ScanAnalysisRepositoryAdapter(repositories.getRepository(ScanAnalysisJpaRepository.class)),
                new ScanRepositoryAdapter(repositories.getRepository(ScanJpaRepository.class)),
                new ScanAnalysisRecomputeRepositoryAdapter(
                        repositories.getRepository(ScanAnalysisRecomputeJpaRepository.class)),
                new TwoPointCatalog(),
                null,
                Runnable::run,
                new UuidV7(clock),
                clock,
                transactionManager);

        userId = jdbc.queryForObject("""
                INSERT INTO app_user (email, full_name, locale, timezone, status, email_verified_at)
                VALUES (?, 'Backfill product line test', 'vi', 'Asia/Ho_Chi_Minh', 'ACTIVE', now())
                RETURNING id
                """, UUID.class, "bf-line-" + UUID.randomUUID() + "@example.test");

        String batch = "BF-" + UUID.randomUUID().toString().substring(0, 8);
        String otherBatch = batch + "-X";
        standardChartId = insertChart("STANDARD", batch, "ACTIVE");
        targetChartId = insertChart("STANDARD", batch, "DRAFT");
        miniChartId = insertChart("MINI", batch, "ACTIVE");
        otherBatchChartId = insertChart("STANDARD", otherBatch, "ACTIVE");

        sameLineScan = insertAnalyzedScan(standardChartId);
        otherLineScan = insertAnalyzedScan(miniChartId);
        otherBatchScan = insertAnalyzedScan(otherBatchChartId);
    }

    @AfterEach
    void cleanUp() {
        List<UUID> charts = java.util.stream.Stream.of(targetChartId, standardChartId, miniChartId, otherBatchChartId)
                .filter(java.util.Objects::nonNull).toList();
        for (UUID chart : charts) {
            jdbc.update("DELETE FROM scan_analysis_recompute WHERE chart_id = ?", chart);
        }
        if (userId != null) {
            jdbc.update("UPDATE scan SET current_analysis_id = NULL WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM scan_analysis WHERE scan_id IN (SELECT id FROM scan WHERE user_id = ?)", userId);
            jdbc.update("DELETE FROM scan WHERE user_id = ?", userId);
        }
        for (UUID chart : charts) {
            jdbc.update("DELETE FROM color_chart WHERE id = ?", chart);
        }
        if (userId != null) {
            jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        }
        if (emf != null) {
            emf.close();
        }
    }

    @Test
    @DisplayName("Backfill bang STANDARD/lo X chi tinh lai scan cua STANDARD/lo X, khong dung MINI hay lo khac")
    void backfillOnlyTouchesScansOfTheSameProductLineAndBatch() {
        UUID otherLineAnalysisBefore = currentAnalysisId(otherLineScan);
        UUID otherBatchAnalysisBefore = currentAnalysisId(otherBatchScan);
        UUID sameLineAnalysisBefore = currentAnalysisId(sameLineScan);

        tx.execute(status -> service.runPreview(context(ChartBackfillService.PREVIEW_JOB), targetChartId,
                Period.ofDays(90)));

        List<UUID> drafted = jdbc.queryForList(
                "SELECT scan_analysis_id FROM scan_analysis_recompute WHERE chart_id = ?", UUID.class, targetChartId);
        assertThat(drafted)
                .as("preview chi duoc tao nhap cho scan cung dong san pham + cung lo voi bang dang backfill")
                .containsExactly(sameLineAnalysisBefore);

        tx.execute(status -> service.runApply(context(ChartBackfillService.APPLY_JOB), targetChartId));

        assertThat(currentAnalysisId(otherLineScan))
                .as("scan cua dong MINI khong duoc bi tro sang ket qua tinh theo bang STANDARD")
                .isEqualTo(otherLineAnalysisBefore);
        assertThat(currentAnalysisId(otherBatchScan))
                .as("scan cua lo khac khong duoc bi ghi de")
                .isEqualTo(otherBatchAnalysisBefore);
        assertThat(currentAnalysisId(sameLineScan))
                .as("scan cung dong + cung lo phai duoc ap dung ket qua moi")
                .isNotEqualTo(sameLineAnalysisBefore);
        assertThat(jdbc.queryForObject("""
                SELECT a.chart_id FROM scan s JOIN scan_analysis a ON a.id = s.current_analysis_id
                 WHERE s.id = ?
                """, UUID.class, sameLineScan)).isEqualTo(targetChartId);
    }


    private UUID insertChart(String productLine, String batch, String status) {
        return jdbc.queryForObject("""
                INSERT INTO color_chart (id, code, version, name, product_line, production_batch, status)
                VALUES (uuidv7(), ?, 1, 'Backfill test', ?, ?, ?)
                RETURNING id
                """, UUID.class, "BF-TEST-" + UUID.randomUUID(), productLine, batch, status);
    }

    private UUID insertAnalyzedScan(UUID chartId) {
        UUID scanId = jdbc.queryForObject("""
                INSERT INTO scan (user_id, assignment, captured_at, store_image, store_image_reason,
                                  status, idempotency_key, chart_id)
                VALUES (?, 'UNASSIGNED', now(), false, 'CONSENT_OFF', 'ANALYZED', ?, ?)
                RETURNING id
                """, UUID.class, userId, "bf-" + UUID.randomUUID(), chartId);
        UUID analysisId = jdbc.queryForObject("""
                INSERT INTO scan_analysis (scan_id, is_current, ph_value, ph_low, ph_high, classification,
                                           confidence, lab_l, lab_a, lab_b, calibration_method,
                                           chart_id, chart_version, engine_version)
                VALUES (?, true, 5.6, 5.4, 5.8, 'LOW', 0.900, 50.0, -10.0, -20.0, 'CARD_CCM', ?, 1, '1.0.0')
                RETURNING id
                """, UUID.class, scanId, chartId);
        jdbc.update("UPDATE scan SET current_analysis_id = ? WHERE id = ?", analysisId, scanId);
        return scanId;
    }

    private UUID currentAnalysisId(UUID scanId) {
        return jdbc.queryForObject("SELECT current_analysis_id FROM scan WHERE id = ?", UUID.class, scanId);
    }

    private static JobContext context(String jobName) {
        return new JobContext(UUID.randomUUID(), jobName, Instant.now(), false);
    }

    private final class TwoPointCatalog implements ChartCatalog {

        @Override
        public Optional<PhChart> findActiveChart(String productLine, String productionBatch) {
            return Optional.empty();
        }

        @Override
        public Optional<PhChart> findChartById(UUID chartId) {
            List<PhChartPoint> points = List.of(
                    new PhChartPoint("00000000-0000-0000-0000-000000000101", 5.5,
                            new Lab(70.0, 20.0, 30.0), 4.0, "#AABBCC", "#AABBCC", "5,5", "5.5", 0, 0.0),
                    new PhChartPoint("00000000-0000-0000-0000-000000000102", 8.0,
                            new Lab(50.0, -10.0, -20.0), 4.0, "#112233", "#112233", "8,0", "8.0", 0, 0.0));
            return Optional.of(new PhChart(chartId.toString(), 1, PhChart.Status.DRAFT,
                    "D65", 2, false, points, DeltaE2000Params.CAT_CHECK));
        }

        @Override
        public List<PhBandClassifier.Band> findGlobalBands() {
            return List.of(
                    band(PhBandClassifier.Code.LOW, null, 6.0, true, false, PhBandClassifier.Severity.WATCH, 1),
                    band(PhBandClassifier.Code.SLIGHTLY_LOW, 6.0, 6.3, true, false, PhBandClassifier.Severity.ATTENTION, 2),
                    band(PhBandClassifier.Code.IN_RANGE, 6.3, 6.6, true, true, PhBandClassifier.Severity.NORMAL, 3),
                    band(PhBandClassifier.Code.SLIGHTLY_HIGH, 6.6, 7.0, false, true, PhBandClassifier.Severity.ATTENTION, 4),
                    band(PhBandClassifier.Code.HIGH, 7.0, null, false, true, PhBandClassifier.Severity.WATCH, 5),
                    band(PhBandClassifier.Code.INCONCLUSIVE, null, null, true, true, PhBandClassifier.Severity.NEUTRAL, 6));
        }

        private PhBandClassifier.Band band(PhBandClassifier.Code code, Double min, Double max,
                                           boolean minInclusive, boolean maxInclusive,
                                           PhBandClassifier.Severity severity, int sortOrder) {
            return new PhBandClassifier.Band(code, min, max, minInclusive, maxInclusive,
                    code.name(), severity, "color-ph-" + sortOrder, "icon", sortOrder);
        }
    }
}
