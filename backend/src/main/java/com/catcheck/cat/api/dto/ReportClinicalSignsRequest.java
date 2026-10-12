package com.catcheck.cat.api.dto;

import java.util.List;

public record ReportClinicalSignsRequest(
        List<String> signs,
        String source,
        String scanId,
        String note
) {
}
