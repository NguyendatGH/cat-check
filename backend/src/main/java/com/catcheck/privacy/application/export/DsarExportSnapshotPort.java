package com.catcheck.privacy.application.export;

import java.util.List;
import java.util.UUID;

public interface DsarExportSnapshotPort {
    String profile(UUID userId);
    List<String> cats(UUID userId);
    List<String> scans(UUID userId);
    List<String> credits(UUID userId);
    List<String> consents(UUID userId);
    List<String> notifications(UUID userId);
    List<String> orders(UUID userId);
}
