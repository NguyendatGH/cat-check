package com.catcheck.credit.domain;

public record ActivationCodeFilter(
        String codePrefix,
        String packageCode,
        ActivationCodeStatus status,
        String productionBatch
) {

    public static ActivationCodeFilter none() {
        return new ActivationCodeFilter(null, null, null, null);
    }

    public ActivationCodeFilter {
        codePrefix = blankToNull(codePrefix);
        packageCode = blankToNull(packageCode);
        productionBatch = blankToNull(productionBatch);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
