package com.catcheck.export.domain.port;

import java.io.InputStream;
import java.util.Optional;

public interface ReportStorage {

    String provider();

    Stored put(String documentCode, byte[] pdf);

    Optional<InputStream> open(String fileRef);

    void delete(String fileRef);

    record Stored(String provider, String fileRef, long bytes) {
    }
}
