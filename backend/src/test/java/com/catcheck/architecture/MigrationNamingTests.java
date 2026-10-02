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
 * <p>Regex đã điều chỉnh so với bản gốc để chấp nhận sub-version dạng "V4.1" (xem
 * {@code V4.1__shedlock.sql} và giải thích quyết định V4b -> V4.1 ở đầu file đó) — Flyway chỉ
 * chấp nhận số ở phần version ({@code ^V\d+__}), không chấp nhận chữ cái như "V4b".</p>
 */
class MigrationNamingTests {

    private static final Pattern MIGRATION_FILENAME_PATTERN = Pattern.compile("^V\\d+(\\.\\d+)?__[a-z0-9_]+\\.sql$");
    private static final Path MIGRATION_DIR = Paths.get("src", "main", "resources", "db", "migration");

    @Test
    void allMigrationFilesMatchNamingConvention() throws IOException {
        List<String> invalid = migrationFileNames().stream()
                .filter(name -> !MIGRATION_FILENAME_PATTERN.matcher(name).matches())
                .toList();

        assertThat(invalid)
                .as("R17: file migration phải khớp ^V\\d+(\\.\\d+)?__[a-z0-9_]+\\.sql$")
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
    void exactlyTheWave1MigrationCatalogExists() throws IOException {
        // Danh mục đúng theo p4 §4.9.2: V1..V4.1 (hạ tầng M0) + V5..V15 (nghiệp vụ).
        //
        // Bản trước của test này bỏ V13 ra với lý do "không nằm trong danh mục p4 §4.9.2" —
        // ghi chú đó SAI: p4 §4.9.2 có đúng dòng `V13 | V13__notification.sql` (notification,
        // notification_outbox, email_outbox, push_subscription, user_notification_preference,
        // reminder; FK health_flag.notification_id). Lúc đó V13 chỉ là CHƯA ai viết, không phải
        // không có trong danh mục — và chính V12 cũng đã để sẵn cột
        // `health_flag.notification_id` kèm comment "FK se duoc V13 bo sung".
        //
        // CHƯA có V16/V17 (trigger/invariant + RLS) — đó là việc của W3, cần bump test này thêm
        // 2 file khi W3 xong, KHÔNG tự thêm sớm.
        assertThat(migrationFileNames())
                .as("R17: đúng danh mục migration sau Wave 1 (chưa có V16/V17 của W3)")
                .containsExactlyInAnyOrder(
                        "V1__extensions_and_functions.sql",
                        "V2__roles_and_grants.sql",
                        "V3__spring_session.sql",
                        "V4__modulith_event_publication.sql",
                        "V4.1__shedlock.sql",
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
                        "V15__ops.sql");
    }

    private String extractVersion(String fileName) {
        return fileName.substring(1, fileName.indexOf("__"));
    }

    private List<String> migrationFileNames() throws IOException {
        assertThat(MIGRATION_DIR).as("thư mục migration phải tồn tại: " + MIGRATION_DIR).isDirectory();
        try (Stream<Path> stream = Files.list(MIGRATION_DIR)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .toList();
        }
    }
}
