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
                        "V23__dsar_export_job.sql",
                        "V24__dsar_download_token.sql",
                        "V25__dsar_email_download_token.sql",
                        "V26__job_run_columns.sql",
                        "V27__community_reaction_multi.sql",
                        "V28__replace_places_with_da_nang.sql",
                        "V29__community_comment_replies_images.sql",
                        "V30__color_chart_active_unique_fix.sql",
                        "V31__credit_refund_unique_ref.sql",
                        "V32__place_booking_active_slot_unique.sql",
                        "V33__community_comment_reaction.sql",
                        "V34__ai_rag_hybrid.sql",
                        "V35__ai_chunk_embedding.sql",
                        "V36__shop_payos_payment.sql");
    }

    @Test
    void allSeedFilesMatchNamingConvention() throws IOException {
        List<String> invalid = seedFileNames().stream()
                .filter(name -> !SEED_FILENAME_PATTERN.matcher(name).matches())
                .toList();

        assertThat(invalid)
                .as("R17: file seed phải khớp ^R__seed_[a-z0-9_]+\\.sql$")
                .isEmpty();
    }

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
