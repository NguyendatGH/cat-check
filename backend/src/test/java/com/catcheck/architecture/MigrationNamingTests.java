package com.catcheck.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R17 — quy tắc đặt tên file migration. Test JUnit thường (không phải ArchUnit) vì đây là
 * kiểm tra tên FILE trong {@code src/main/resources/db/migration}, không phải kiểm tra class.
 *
 * <p><b>Regex nới một nhánh {@code (_\d+)?} so với văn bản p7 §7.6.2</b>
 * ({@code ^V\d+__[a-z0-9_]+\.sql$}). Lý do, theo đúng thứ tự:</p>
 *
 * <ol>
 *   <li><b>p4 thắng về miền migration.</b> {@code context/spec/04-index.md} §2 giao miền
 *       migration cho p4; p4 §4.9.2 đặt {@code shedlock} ở khe giữa V4 và V5, và đặt trước
 *       V16 cho trigger/bất biến, V17 cho RLS. Hai số đó KHÔNG được chiếm.</li>
 *   <li><b>Nhưng tên literal "V4b" của p4 §4.9.2 không chạy được.</b> Flyway BỎ QUA file
 *       trong im lặng vì "4b" không phải version hợp lệ. Đo thật bằng
 *       {@code SchemaInvariantTests}: {@code V4b__shedlock.sql} ⇒ 23 migration, 60 bảng,
 *       KHÔNG có bảng {@code shedlock}; {@code V4_1__shedlock.sql} ⇒ 24 migration, 61 bảng.
 *       Không có cảnh báo nào — hỏng chỉ lộ lúc ShedLock chạy job đầu tiên.</li>
 *   <li><b>Nên dùng {@code V4_1}</b>: Flyway coi "_" là dấu phân tách version hợp lệ (tương
 *       đương "."), nên file này là version <b>4.1</b> — đúng khe giữa V4 và V5 mà p4 muốn,
 *       và V16/V17 vẫn nguyên vẹn cho trigger và RLS.</li>
 * </ol>
 *
 * <p>Regex ở đây vì vậy nới ĐÚNG một nhóm số phụ tuỳ chọn, không nới gì khác: chữ cái trong
 * version vẫn bị chặn (và phải bị chặn — xem mục 2). <b>Văn bản p7 §7.6.2 cần sửa cho khớp
 * p4</b> — handoff H15.33 / H15.65.</p>
 */
class MigrationNamingTests {

    private static final Pattern MIGRATION_FILENAME_PATTERN = Pattern.compile("^V\\d+(_\\d+)?__[a-z0-9_]+\\.sql$");
    private static final Pattern SEED_FILENAME_PATTERN = Pattern.compile("^R__seed_[a-z0-9_]+\\.sql$");
    private static final Path MIGRATION_DIR = Paths.get("src", "main", "resources", "db", "migration");
    private static final Path SEED_DIR = Paths.get("src", "main", "resources", "db", "seed");

    @Test
    void allMigrationFilesMatchNamingConvention() throws IOException {
        List<String> invalid = migrationFileNames().stream()
                .filter(name -> !MIGRATION_FILENAME_PATTERN.matcher(name).matches())
                .toList();

        assertThat(invalid)
                .as("R17 (nới theo p4 §4.9.2): file migration phải khớp ^V\\d+(_\\d+)?__[a-z0-9_]+\\.sql$")
                .isEmpty();
    }

    @Test
    void noTwoMigrationFilesShareTheSameVersion() throws IOException {
        List<String> versions = migrationFileNames().stream()
                .map(this::extractVersion)
                .toList();

        assertThat(versions)
                .as("R17: không hai file migration nào được trùng version")
                .doesNotHaveDuplicates();
    }

    @Test
    void migrationCatalogContainsTheApprovedWave1AndFeatureMigrations() throws IOException {
        // V18–V22 là schema mở rộng đã được owner yêu cầu cho community, place, shop và AI RAG.
        //
        // `V4_1__shedlock.sql` (Flyway đọc là version 4.1) giữ đúng khe thứ tự "ngay sau V4,
        // trước V5" mà p4 §4.9.2 đặt cho `shedlock`; chỉ phần TÊN lệch khỏi literal "V4b" vì
        // Flyway bỏ qua file đó trong im lặng (bằng chứng đo được ghi ở javadoc lớp này và ở
        // đầu chính file V4_1).
        //
        // V17 vẫn để trống cho RLS; các tính năng Phase 2/3 dùng V18–V21 để không chiếm khe đó.
        assertThat(migrationFileNames())
                .as("R17: đúng danh mục migration sau W1-A (chưa có V17__rls.sql của owner)")
                .containsExactlyInAnyOrder(
                        "V1__extensions_and_functions.sql",
                        "V2__roles_and_grants.sql",
                        "V3__spring_session.sql",
                        "V4__modulith_event_publication.sql",
                        "V4_1__shedlock.sql",
                        "V5__identity.sql",
                        "V6__privacy.sql",
                        "V7__catalog.sql",
                        "V8__cat.sql",
                        "V9__colorchart.sql",
                        "V10__credit.sql",
                        "V11__scan.sql",
                        "V12__monitoring.sql",
                        "V13__notification.sql",
                        "V14__export.sql",
                        "V15__ops.sql",
                        "V16__triggers_and_invariants.sql",
                        "V18__community.sql",
                        "V19__place.sql",
                        "V20__shop.sql",
                        "V21__ai_rag.sql",
                        "V22__ai_document_lifecycle.sql",
                        // V23–V25: DSAR export job + token tải một lần (W5 khác).
                        "V23__dsar_export_job.sql",
                        "V24__dsar_download_token.sql",
                        "V25__dsar_email_download_token.sql",
                        // V26 (W5-D): 6 cột + SKIPPED_THRESHOLD của `job_run` mà `V15__ops.sql`
                        // còn thiếu so với p4 §K3 / p12 §12.8.2 — handoff H15.44. Là NGOẠI LỆ duy
                        // nhất của đợt này đối với quy tắc "chỉ thêm migration có trong p4 §4.9.2",
                        // và nó tồn tại vì p8 L65 đặc tả `trigger_type = MANUAL`: không có cột
                        // thật thì endpoint đó không làm được đúng việc của nó. Handoff H15.180.
                        "V26__job_run_columns.sql");
    }

    /**
     * R17 vế thứ hai: "Trong {@code db/seed} phải khớp {@code R__seed_[a-z0-9_]+\.sql}"
     * (p7 §7.6.2). Trước W1-A chưa có test nào cho vế này.
     */
    @Test
    void allSeedFilesMatchNamingConvention() throws IOException {
        List<String> invalid = seedFileNames().stream()
                .filter(name -> !SEED_FILENAME_PATTERN.matcher(name).matches())
                .toList();

        assertThat(invalid)
                .as("R17: file seed phải khớp ^R__seed_[a-z0-9_]+\\.sql$")
                .isEmpty();
    }

    /**
     * p4 §4.9.2 liệt kê 11 file seed. Bốn file (`cat_breed`, `monitoring_rule`,
     * `ph_classification_band`, `color_chart_placeholder`) hiện CHƯA tồn tại dưới dạng `R__`
     * vì dữ liệu của chúng nằm trong V7/V9/V12 — trái p4 §4.9.1 ("V__ chỉ schema"), đã ghi
     * handoff H15 và KHÔNG sửa ở gói này (sửa = vỡ checksum của migration đã merge).
     */
    @Test
    void seedCatalogMatchesSpecMinusTheOnesStillInlinedInVersionedMigrations() throws IOException {
        assertThat(seedFileNames())
                .as("p4 §4.9.2: danh mục file seed hiện có")
                .containsExactlyInAnyOrder(
                        "R__seed_app_setting.sql",
                        "R__seed_consent_purpose.sql",
                        "R__seed_data_inventory_item.sql",
                        "R__seed_holiday_calendar.sql",
                        "R__seed_package_plan.sql",
                        "R__seed_policy_version.sql",
                        "R__seed_retention_policy.sql");
    }

    private String extractVersion(String fileName) {
        return fileName.substring(1, fileName.indexOf("__"));
    }

    private List<String> migrationFileNames() throws IOException {
        return fileNamesIn(MIGRATION_DIR);
    }

    private List<String> seedFileNames() throws IOException {
        return fileNamesIn(SEED_DIR);
    }

    private List<String> fileNamesIn(Path dir) throws IOException {
        assertThat(dir).as("thư mục phải tồn tại: " + dir).isDirectory();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .toList();
        }
    }
}
