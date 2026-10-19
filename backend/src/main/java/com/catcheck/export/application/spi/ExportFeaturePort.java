package com.catcheck.export.application.spi;

import java.util.UUID;

public interface ExportFeaturePort {

    boolean isExportEnabled(UUID userId);
}
