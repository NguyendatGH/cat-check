package com.catcheck.export.infrastructure.report;

import com.catcheck.export.domain.port.ReportStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Hiện thực {@link ReportStorage} cho giai đoạn 1 — p13 §13.6.4: thư mục riêng
 * {@code <catcheck.storage.local.root>/reports/}, tách khỏi ảnh scan.
 *
 * <p>Đọc khoá cấu hình {@code catcheck.storage.local.root} bằng placeholder thay vì inject
 * {@code media.infrastructure.MediaProperties}: lớp đó là internal của module {@code media},
 * import nó sẽ vi phạm biên module (p7 §7.4.1). Dùng cùng một khoá để hai loại tệp nằm cùng một
 * gốc lưu trữ khi vận hành đổi đĩa, nhưng khác thư mục con.</p>
 *
 * <p>Ghi theo kiểu ghi-tạm-rồi-đổi-tên ({@code ATOMIC_MOVE}) nên không bao giờ để lại file PDF
 * nửa vời cho {@code GET /exports/{id}/download} đọc phải.</p>
 *
 * <p><b>Vì sao package tên {@code report} chứ không {@code storage}:</b> {@code .gitignore} của
 * repo có dòng {@code storage/}, khớp MỌI thư mục tên đó — kể cả thư mục mã nguồn. Lớp này đặt ở
 * {@code infrastructure/storage/} thì biên dịch và chạy bình thường nhưng <b>không bao giờ vào
 * được git</b> (đã kiểm bằng {@code git check-ignore}).</p>
 */
@Component
public class LocalFileSystemReportStorage implements ReportStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemReportStorage.class);

    /** Tên file chỉ từ mã tài liệu đã chuẩn hoá — chặn mọi ký tự có thể đi ra ngoài thư mục. */
    private static final Pattern SAFE_REF = Pattern.compile("[a-z0-9]{4}/[0-9]{2}/[a-z0-9._-]{1,80}");

    private final Path root;
    private final Clock clock;

    public LocalFileSystemReportStorage(
            @Value("${catcheck.storage.local.root:./data/media}") Path storageRoot,
            Clock clock) {
        this.root = storageRoot.resolve("reports").toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Override
    public String provider() {
        return "LOCAL";
    }

    @Override
    public Stored put(String documentCode, byte[] pdf) {
        var date = clock.instant().atZone(ZoneOffset.UTC);
        String name = documentCode.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-") + ".pdf";
        String fileRef = "%04d/%02d/%s".formatted(date.getYear(), date.getMonthValue(), name);
        Path target = resolve(fileRef).orElseThrow(
                () -> new IllegalStateException("fileRef khong hop le: " + fileRef));
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".report-", ".tmp");
            Files.write(tmp, pdf);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Khong ghi duoc file bao cao", e);
        }
        return new Stored(provider(), fileRef, pdf.length);
    }

    @Override
    public Optional<InputStream> open(String fileRef) {
        Optional<Path> path = resolve(fileRef);
        if (path.isEmpty() || !Files.isRegularFile(path.get())) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.newInputStream(path.get()));
        } catch (IOException e) {
            log.warn("Khong mo duoc file bao cao {}", fileRef);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String fileRef) {
        resolve(fileRef).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("Khong xoa duoc file bao cao {}", fileRef);
            }
        });
    }

    /** Rỗng nếu tham chiếu không khớp khuôn an toàn hoặc trỏ ra ngoài {@link #root}. */
    private Optional<Path> resolve(String fileRef) {
        if (fileRef == null || !SAFE_REF.matcher(fileRef).matches()) {
            return Optional.empty();
        }
        Path resolved = root.resolve(fileRef).normalize();
        return resolved.startsWith(root) ? Optional.of(resolved) : Optional.empty();
    }
}
