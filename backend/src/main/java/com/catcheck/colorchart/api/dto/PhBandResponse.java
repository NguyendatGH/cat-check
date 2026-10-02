package com.catcheck.colorchart.api.dto;

import com.catcheck.colorchart.domain.BandSeverity;
import com.catcheck.colorchart.domain.PhClassificationBand;

/**
 * Dải phân loại pH trả về client (F1, L36).
 *
 * <p>Field {@code messageKey} là khoá i18n để frontend tra nhãn theo locale — nhãn không nằm
 * trong response vì nó đã seed trong DB và frontend có thể cache theo locale.
 */
public record PhBandResponse(
        String code,
        Double phMin,
        Double phMax,
        boolean minInclusive,
        boolean maxInclusive,
        String severity,
        String label,
        String description,
        String colorToken,
        String iconName,
        int sortOrder,
        boolean triggersAlert) {

    public static PhBandResponse from(PhClassificationBand band, String locale) {
        boolean en = "en".equals(locale);
        return new PhBandResponse(
                band.getCode(),
                band.getMinPh() != null ? band.getMinPh().doubleValue() : null,
                band.getMaxPh() != null ? band.getMaxPh().doubleValue() : null,
                band.isMinInclusive(),
                band.isMaxInclusive(),
                band.getSeverity().name(),
                en ? (band.getLabelEn() != null ? band.getLabelEn() : band.getLabelVi()) : band.getLabelVi(),
                en ? (band.getDescriptionEn() != null ? band.getDescriptionEn() : band.getDescriptionVi())
                        : band.getDescriptionVi(),
                band.getColorToken(),
                band.getIconName(),
                band.getSortOrder(),
                band.isTriggersAlert());
    }
}
