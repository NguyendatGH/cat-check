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

@Component
public class LocalFileSystemReportStorage implements ReportStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemReportStorage.class);

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

    private Optional<Path> resolve(String fileRef) {
        if (fileRef == null || !SAFE_REF.matcher(fileRef).matches()) {
            return Optional.empty();
        }
        Path resolved = root.resolve(fileRef).normalize();
        return resolved.startsWith(root) ? Optional.of(resolved) : Optional.empty();
    }
}
