package com.catcheck.insight.infrastructure.persistence;

import com.catcheck.insight.domain.HealthFlag;
import com.catcheck.insight.domain.HealthFlagSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trùng {@code dedupe_key} trong transaction của {@code POST /scans} không được làm hỏng
 * transaction đó.
 *
 * <p>Bản cũ INSERT rồi bắt {@code DataIntegrityViolationException}; trên Postgres thật câu lệnh
 * kế tiếp nhận "current transaction is aborted" (đã thử bằng psql) nên lần quét thứ ba liên tiếp
 * cùng cooldown trả 500. Không Docker — nối Postgres local như
 * {@code JdbcShopRepositoryCheckoutRaceIntegrationTest}.</p>
 */
class HealthFlagDedupeIntegrationTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/catcheck";
    private static final String DB_USER = "catcheck";
    private static final String DB_PASSWORD = "catcheck_local_only";

    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private HealthFlagRepositoryAdapter adapter;
    private UUID userId;
    private UUID catId;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(JDBC_URL, DB_USER, DB_PASSWORD);
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        adapter = new HealthFlagRepositoryAdapter(null, jdbc, JsonMapper.builder().build(), Clock.systemUTC());
        userId = jdbc.queryForObject("""
                INSERT INTO app_user (email, full_name, locale, timezone, status, email_verified_at)
                VALUES (?, 'Health flag dedupe test', 'vi', 'Asia/Ho_Chi_Minh', 'ACTIVE', now())
                RETURNING id
                """, UUID.class, "hf-dedupe-" + UUID.randomUUID() + "@example.test");
        catId = jdbc.queryForObject("""
                INSERT INTO cat (owner_id, name, approx_age_months, public_code, status)
                VALUES (?, 'Dedupe', 24, ?, 'ACTIVE')
                RETURNING id
                """, UUID.class, userId, publicCode());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM cat WHERE id = ?", catId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
    }

    @Test
    void duplicateDedupeKeyIsSkippedWithoutAbortingTheSurroundingTransaction() {
        String dedupeKey = "LOW_QUALITY_STREAK:" + catId + ":1";

        Long countInsideTx = tx.execute(status -> {
            assertThat(adapter.saveIfNotDuplicate(flag(dedupeKey))).isPresent();
            Optional<HealthFlag> second = adapter.saveIfNotDuplicate(flag(dedupeKey));
            assertThat(second).isEmpty();
            // Câu lệnh tiếp theo trong CÙNG transaction — đúng chỗ rule kế tiếp đọc lịch sử scan.
            return jdbc.queryForObject("SELECT count(*) FROM health_flag WHERE cat_id = ?", Long.class, catId);
        });

        assertThat(countInsideTx).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM health_flag WHERE cat_id = ?", Long.class, catId))
                .as("transaction phải commit được, không bị rollback-only")
                .isEqualTo(1L);
    }

    private HealthFlag flag(String dedupeKey) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return new HealthFlag(UUID.randomUUID(), catId, "LOW_QUALITY_STREAK", null, HealthFlagSeverity.INFO,
                now.minusSeconds(3600), now, "rule.LOW_QUALITY_STREAK.message", Map.of("n", 2),
                "test", dedupeKey, now);
    }

    /** {@code ck_cat_public_code}: {@code CC-VN-} + 6 ký tự Crockford Base32. */
    private static String publicCode() {
        String alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
        StringBuilder code = new StringBuilder("CC-VN-");
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return code.toString();
    }
}
