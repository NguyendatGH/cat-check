package com.catcheck.privacy.infrastructure.persistence;

import com.catcheck.privacy.application.export.DsarExportStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class LocalDsarExportStorage implements DsarExportStorage {
    private static final Pattern REF = Pattern.compile("DSAR-[0-9]{4}-[0-9]{6,}");
    private static final Pattern KEY = Pattern.compile("[0-9]{4}/[0-9]{2}/DSAR-[0-9]{4}-[0-9]{6,}\\.zip");
    private final Path root;
    private final Clock clock;

    public LocalDsarExportStorage(@Value("${catcheck.storage.local.root:./data/media}") Path root, Clock clock) {
        this.root = root.resolve("dsar-exports").toAbsolutePath().normalize();
        this.clock = clock;
    }
    @Override public String put(String publicRef, byte[] bytes) {
        if (!REF.matcher(publicRef).matches()) throw new IllegalArgumentException("invalid publicRef");
        var date = clock.instant().atZone(ZoneOffset.UTC);
        String key = "%04d/%02d/%s.zip".formatted(date.getYear(), date.getMonthValue(), publicRef);
        Path target = resolve(key).orElseThrow();
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".dsar-", ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return key;
        } catch (IOException ex) { throw new IllegalStateException("Unable to persist DSAR archive", ex); }
    }
    @Override public Optional<InputStream> open(String key) {
        return resolve(key).filter(Files::isRegularFile).flatMap(path -> {
            try { return Optional.of(Files.newInputStream(path)); } catch (IOException ex) { return Optional.empty(); }
        });
    }
    @Override public void delete(String key) {
        resolve(key).ifPresent(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } });
    }
    private Optional<Path> resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) return Optional.empty();
        Path path = root.resolve(key).normalize();
        return path.startsWith(root) ? Optional.of(path) : Optional.empty();
    }
}
