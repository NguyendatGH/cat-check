package com.catcheck.shared.application;

import com.catcheck.shared.application.spi.AppSettingReader;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PublicSystemStatusService {

    static final String KEY_BUILD_VERSION = "app.build_version";
    static final String KEY_MAINTENANCE_ACTIVE = "app.maintenance_active";
    static final String KEY_MAINTENANCE_UNTIL = "app.maintenance_until";

    static final String NAMESPACE_FEATURE = "feature";

    static final String UNKNOWN_VERSION = "unknown";

    private final AppSettingReader appSettingReader;

    public PublicSystemStatusService(AppSettingReader appSettingReader) {
        this.appSettingReader = appSettingReader;
    }

    public String buildVersion() {
        return appSettingReader.findPublicValue(KEY_BUILD_VERSION)
                .filter(v -> !v.isBlank())
                .orElse(UNKNOWN_VERSION);
    }

    public boolean maintenanceActive() {
        return appSettingReader.findPublicValue(KEY_MAINTENANCE_ACTIVE)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    public String maintenanceUntil() {
        return appSettingReader.findPublicValue(KEY_MAINTENANCE_UNTIL)
                .filter(v -> !v.isBlank())
                .orElse(null);
    }

    public Map<String, String> publicFeatureFlags() {
        return appSettingReader.findPublicNamespace(NAMESPACE_FEATURE);
    }
}
