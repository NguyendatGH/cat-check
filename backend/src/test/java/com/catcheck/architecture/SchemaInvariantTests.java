package com.catcheck.architecture;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm tra bắt buộc sau lần migrate đầu tiên — p4 §4.9.4.
 *
 * <p>Test chạy Flyway THẬT (cùng `locations` với {@code application.yml}) trên một PostgreSQL
 * 18.6 rỗng qua Testcontainers, rồi soi catalog. Không dùng Spring context: thứ cần kiểm là
 * schema và dữ liệu seed, không phải bean — chạy Flyway trực tiếp giữ test nhanh và không phụ
 * thuộc tình trạng wiring của các module khác.</p>
 *
 * <p>Vì sao PostgreSQL thật chứ không H2: `uuidv7()`, `jsonb`, partial index, `EXCLUDE USING
 * gist` và trigger PL/pgSQL đều không có hành vi tương đương ở H2
 * ({@code reference/research-integrations.md}).</p>
 */
@Testcontainers
class SchemaInvariantTests {

    /**
     * p4 §4.9.4 mục 2: 56 bảng do Part 4 đặc tả (A:9 · B:10 · C:5 · D:13 · E:5 · F:6 · G:1 ·
     * H:3 · K:4) + 4 bảng framework (SPRING_SESSION, SPRING_SESSION_ATTRIBUTES,
     * event_publication, shedlock) + flyway_schema_history + 5 bảng RAG = 77, + 1 bảng của
     * V23–V25 (DSAR export job) = 78.
     *
     * <p><b>Con số này là một điểm nóng khi nhiều nhánh chạy song song.</b> Mỗi migration thêm
     * bảng đều phải sửa nó, nên nó đỏ ngay khi một nhánh khác merge trước. Giữ nguyên dạng con số
     * cố định là CỐ Ý: một ngưỡng mềm ("≥ 77") sẽ không còn bắt được việc tạo bảng ngoài danh mục
     * p4 §4.9.2 — đúng thứ test này tồn tại để bắt.</p>
     */
    private static final int EXPECTED_TABLE_COUNT = 78;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-trixie");

    private static MigrateResult migrateResult;

    @BeforeAll
    static void migrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load();
        migrateResult = flyway.migrate();
    }

    /** p4 §4.9.4 mục 1: không còn migration nào `Pending`. */
    @Test
    void migratesCleanlyFromAnEmptyDatabase() {
        assertThat(migrateResult.success).as("Flyway migrate phải thành công").isTrue();
        // V1..V16 + V18..V27 (28 versioned) + 7 file R__ repeatable.
        // V23–V25: DSAR export job (W5 khác). V26: 6 cột `job_run` của p4 §K3 (W5-D, H15.44/H15.180).
        assertThat(migrateResult.migrationsExecuted)
                .as("số migration áp dụng trên DB rỗng")
                .isEqualTo(34);
    }

    /** p4 §4.9.4 mục 2. */
    @Test
    void tableCountMatchesPart4() throws SQLException {
        assertThat(scalarInt("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"))
                .as("p4 §4.9.4: 56 bảng nghiệp vụ + 4 bảng framework + flyway_schema_history")
                .isEqualTo(EXPECTED_TABLE_COUNT);
    }

    /**
     * p4 §4.9.4 mục 4 + §4.9.2 dòng V16: "mọi bảng có `updated_at` đều có trigger".
     * Đây là lưới bắt khi ai đó thêm bảng mới sau V16 mà quên tạo trigger.
     */
    @Test
    void everyTableWithUpdatedAtHasTheSetUpdatedAtTrigger() throws SQLException {
        List<String> missing = strings("""
                SELECT c.table_name
                FROM information_schema.columns c
                         JOIN information_schema.tables t
                              ON t.table_schema = c.table_schema
                                  AND t.table_name = c.table_name
                                  AND t.table_type = 'BASE TABLE'
                WHERE c.table_schema = 'public'
                  AND c.column_name = 'updated_at'
                  AND NOT EXISTS (SELECT 1
                                  FROM pg_trigger tg
                                           JOIN pg_class cl ON cl.oid = tg.tgrelid
                                           JOIN pg_proc pr ON pr.oid = tg.tgfoid
                                  WHERE cl.relname = c.table_name
                                    AND NOT tg.tgisinternal
                                    AND pr.proname = 'set_updated_at')
                ORDER BY 1
                """);

        assertThat(missing)
                .as("bảng có cột updated_at nhưng thiếu trigger set_updated_at (p4 §4.9.2 V16)")
                .isEmpty();
    }

    /** Trigger phải thực sự chạy, không chỉ tồn tại trong catalog. */
    @Test
    void setUpdatedAtTriggerActuallyBumpsTheColumn() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("UPDATE app_setting SET updated_at = TIMESTAMPTZ '2000-01-01' "
                    + "WHERE key = 'cat.max_per_user'");
            st.execute("UPDATE app_setting SET description = description "
                    + "WHERE key = 'cat.max_per_user'");
            try (ResultSet rs = st.executeQuery(
                    "SELECT updated_at > TIMESTAMPTZ '2020-01-01' FROM app_setting "
                            + "WHERE key = 'cat.max_per_user'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean(1)).as("trigger set_updated_at phải cập nhật updated_at").isTrue();
            }
        }
    }

    /** Bất biến I7 (p4 §4.5.1) — `scan.store_image = false` ⇒ không được có `scan_image`. */
    @Test
    void invariantI7BlocksScanImageWhenStoreImageIsFalse() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute("""
                    INSERT INTO app_user (id, email, full_name)
                    VALUES ('01a10000-0000-7000-8000-00000000001a'::uuid, 'i7@example.invalid', 'I7')
                    """);
            st.execute("""
                    INSERT INTO scan (id, user_id, captured_at, capture_source, status,
                                      idempotency_key, store_image, store_image_reason)
                    VALUES ('01a10000-0000-7000-8000-00000000002a'::uuid,
                            '01a10000-0000-7000-8000-00000000001a'::uuid,
                            now(), 'CAMERA', 'PENDING', 'i7-no-image', false, 'CONSENT_OFF')
                    """);
            st.execute("""
                    INSERT INTO scan (id, user_id, captured_at, capture_source, status,
                                      idempotency_key, store_image)
                    VALUES ('01a10000-0000-7000-8000-00000000003a'::uuid,
                            '01a10000-0000-7000-8000-00000000001a'::uuid,
                            now(), 'CAMERA', 'PENDING', 'i7-with-image', true)
                    """);

            // store_image = true ⇒ chèn được.
            st.execute(insertScanImage("01a10000-0000-7000-8000-00000000003a", "key/ok"));

            // store_image = false ⇒ trigger chặn.
            assertThatThrownBy(() -> st.execute(
                    insertScanImage("01a10000-0000-7000-8000-00000000002a", "key/blocked")))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("I7");

            st.execute("DELETE FROM scan_image");
            st.execute("DELETE FROM scan");
            st.execute("DELETE FROM app_user");
        }
    }

    /** Bất biến I9 (p4 §4.5.1) — hai dải ACTIVE cùng `chart_id` không được chồng lấn. */
    @Test
    void invariantI9BlocksOverlappingActiveBands() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            st.execute(insertBand("IN_RANGE", "6.3", "6.6", true, true, 1));
            assertThatThrownBy(() -> st.execute(insertBand("SLIGHTLY_HIGH", "6.5", "6.7", true, true, 2)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ex_ph_classification_band_i9_no_overlap");
            // Dải không giao nhau thì vẫn chèn được.
            st.execute(insertBand("HIGH", "6.6", "7.2", false, true, 3));

            st.execute("DELETE FROM ph_classification_band WHERE chart_id IS NOT NULL");
        }
    }

    /** Bất biến I31 (p4 §4.5.1) — PUBLISHED + claim ≠ NONE thì `source_reference` ≥ 10 ký tự. */
    @Test
    void invariantI31BlocksPublishedClaimWithoutSource() throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.execute("""
                    INSERT INTO care_tip (slug, locale, kind, title, status, claim_type)
                    VALUES ('i31-no-source', 'vi', 'TIP', 'x', 'PUBLISHED', 'MEDICAL')
                    """))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("ck_care_tip_i31_published_claim_needs_source");

            st.execute("""
                    INSERT INTO care_tip (slug, locale, kind, title, status, claim_type, source_reference)
                    VALUES ('i31-with-source', 'vi', 'TIP', 'x', 'PUBLISHED', 'MEDICAL',
                            'Nguon tham chieu day du')
                    """);
            st.execute("DELETE FROM care_tip WHERE slug = 'i31-with-source'");
        }
    }

    /** Thủ tục ẩn danh hoá của p4 §4.6.2 phải tồn tại, là SECURITY DEFINER và PUBLIC không chạy được. */
    @Test
    void anonymizationProcedureIsSecurityDefinerAndNotExecutableByPublic() throws SQLException {
        assertThat(scalarInt("""
                SELECT count(*) FROM pg_proc
                WHERE proname = 'anonymize_user_append_only' AND prosecdef
                """))
                .as("p4 §4.6.2: stored procedure SECURITY DEFINER phục vụ ẩn danh hoá")
                .isEqualTo(1);

        assertThat(scalarInt("""
                SELECT count(*) FROM pg_proc
                WHERE proname = 'anonymize_user_append_only'
                  AND has_function_privilege('public', oid, 'EXECUTE')
                """))
                .as("REVOKE ALL ... FROM PUBLIC phải có hiệu lực")
                .isZero();
    }

    /** Số dòng seed — p4 §4.9.2 bảng "Seed (repeatable…)". */
    @Test
    void referenceDataIsSeeded() throws SQLException {
        // 25 khoá ở p4 §H3 + 6 kill-switch ở §4.3b.1.
        assertThat(scalarInt("SELECT count(*) FROM app_setting")).isEqualTo(31);
        // D1…D21 theo p15 §15.2.2.
        assertThat(scalarInt("SELECT count(*) FROM data_inventory_item")).isEqualTo(21);
        assertThat(scalarInt("SELECT count(*) FROM data_inventory_item WHERE code = 'D21'")).isEqualTo(1);
        // ~18 chính sách theo p15 §15.5.1 (19 dòng; hai dòng chưa có đích lưu ở Phase 1 — xem
        // ghi chú đầu R__seed_retention_policy.sql).
        assertThat(scalarInt("SELECT count(*) FROM retention_policy")).isEqualTo(19);
        // Vòng khoá ngoại thứ hai (p4 §4.9.3) phải được nối lại sau khi cả hai file seed chạy.
        assertThat(scalarInt("SELECT count(*) FROM data_inventory_item WHERE retention_policy_code IS NOT NULL"))
                .isEqualTo(9);
        // Lịch nghỉ lễ VN 2026 + 2027.
        assertThat(scalarInt("SELECT count(*) FROM holiday_calendar WHERE country_code = 'VN'")).isEqualTo(33);
        assertThat(scalarInt("SELECT count(DISTINCT extract(year FROM holiday_date)) FROM holiday_calendar"))
                .as("p4 §4.9.2: ngày lễ của năm hiện tại VÀ năm sau")
                .isEqualTo(2);
        // Hai bất biến về thời hạn (I15) không được nới bằng cấu hình.
        assertThat(scalarInt("SELECT (value #>> '{}')::int FROM app_setting WHERE key = 'scan.image_retention_days'"))
                .isLessThanOrEqualTo(14);
        assertThat(scalarInt("SELECT (value #>> '{}')::int FROM app_setting WHERE key = 'account.deletion_grace_days'"))
                .isLessThanOrEqualTo(7);
    }

    private static String insertScanImage(String scanId, String storageKey) {
        return "INSERT INTO scan_image (id, scan_id, storage_provider, storage_key, content_type, "
                + "bytes, width, height, expires_at) VALUES (uuidv7(), '" + scanId + "'::uuid, "
                + "'LOCAL', '" + storageKey + "', 'image/jpeg', 100, 10, 10, now() + interval '14 days')";
    }

    private static String insertBand(String code, String minPh, String maxPh,
                                     boolean minInclusive, boolean maxInclusive, int sortOrder) {
        return "INSERT INTO ph_classification_band (id, chart_id, code, min_ph, max_ph, "
                + "min_inclusive, max_inclusive, label_vi, severity, color_token, icon_name, "
                + "triggers_alert, sort_order, active) "
                + "SELECT uuidv7(), cc.id, '" + code + "', " + minPh + ", " + maxPh + ", "
                + minInclusive + ", " + maxInclusive + ", 'x', 'NORMAL', 'c', 'i', false, "
                + sortOrder + ", true FROM color_chart cc LIMIT 1";
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static int scalarInt(String sql) throws SQLException {
        try (Connection c = connection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).as("truy vấn phải trả một dòng: " + sql).isTrue();
            return rs.getInt(1);
        }
    }

    private static List<String> strings(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = connection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
