package com.catcheck.privacy.application.export;

import java.io.InputStream;
import java.util.Optional;

public interface DsarExportStorage {
    String put(String publicRef, byte[] zipBytes);
    Optional<InputStream> open(String storageKey);
    void delete(String storageKey);
}
