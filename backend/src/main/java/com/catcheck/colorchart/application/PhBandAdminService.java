package com.catcheck.colorchart.application;

import com.catcheck.colorchart.api.ColorChartErrorCode;
import com.catcheck.colorchart.domain.BandSeverity;
import com.catcheck.colorchart.domain.PhClassificationBand;
import com.catcheck.colorchart.domain.port.PhClassificationBandRepository;
import com.catcheck.colorchart.api.ColorChartErrorCode;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Service
public class PhBandAdminService {

    private final PhClassificationBandRepository bandRepository;
    private final Clock clock;

    public PhBandAdminService(PhClassificationBandRepository bandRepository, Clock clock) {
        this.bandRepository = bandRepository;
        this.clock = clock;
    }


    @Transactional(readOnly = true)
    public List<PhClassificationBand> listBands() {
        return bandRepository.findGlobalActiveOrderBySortOrder();
    }


    @Transactional
    public PhClassificationBand updateBand(String code, BandUpdate update) {
        PhClassificationBand band = bandRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException(ColorChartErrorCode.PH_BAND_NOT_FOUND, code));

        if (update.minPh() != null) {
            band.setMinPh(update.minPh());
        }
        if (update.maxPh() != null) {
            band.setMaxPh(update.maxPh());
        }
        if (update.minInclusive() != null) {
            band.setMinInclusive(update.minInclusive());
        }
        if (update.maxInclusive() != null) {
            band.setMaxInclusive(update.maxInclusive());
        }
        if (update.labelVi() != null) {
            band.setLabelVi(update.labelVi());
        }
        if (update.labelEn() != null) {
            band.setLabelEn(update.labelEn());
        }
        if (update.descriptionVi() != null) {
            band.setDescriptionVi(update.descriptionVi());
        }
        if (update.descriptionEn() != null) {
            band.setDescriptionEn(update.descriptionEn());
        }
        if (update.severity() != null) {
            band.setSeverity(update.severity());
        }
        if (update.colorToken() != null) {
            band.setColorToken(update.colorToken());
        }
        if (update.iconName() != null) {
            band.setIconName(update.iconName());
        }
        if (update.triggersAlert() != null) {
            band.setTriggersAlert(update.triggersAlert());
        }
        if (update.sortOrder() != null) {
            band.setSortOrder(update.sortOrder());
        }
        if (update.active() != null) {
            band.setActive(update.active());
        }

        validateCoverage(band);
        band.markUpdated(clock.instant());
        return bandRepository.save(band);
    }

    private void validateCoverage(PhClassificationBand updated) {
        if (!updated.isActive()) {
            return;
        }
        if (updated.getMinPh() == null && updated.getMaxPh() == null) {
            return;
        }

        List<PhClassificationBand> siblings = updated.getChartId() == null
                ? bandRepository.findGlobalActiveOrderBySortOrder()
                : bandRepository.findByChartIdOrderBySortOrder(updated.getChartId());

        List<PhClassificationBand> others = new ArrayList<>();
        for (PhClassificationBand band : siblings) {
            if (!band.getId().equals(updated.getId()) && band.isActive()
                    && (band.getMinPh() != null || band.getMaxPh() != null)) {
                others.add(band);
            }
        }

        for (PhClassificationBand other : others) {
            if (overlaps(updated, other)) {
                throw new BusinessRuleException(ColorChartErrorCode.PH_BAND_OVERLAP,
                        updated.getCode(), other.getCode());
            }
        }
    }

    private boolean overlaps(PhClassificationBand a, PhClassificationBand b) {
        BigDecimal aMin = a.getMinPh() != null ? a.getMinPh() : BigDecimal.valueOf(-Double.MAX_VALUE);
        BigDecimal aMax = a.getMaxPh() != null ? a.getMaxPh() : BigDecimal.valueOf(Double.MAX_VALUE);
        BigDecimal bMin = b.getMinPh() != null ? b.getMinPh() : BigDecimal.valueOf(-Double.MAX_VALUE);
        BigDecimal bMax = b.getMaxPh() != null ? b.getMaxPh() : BigDecimal.valueOf(Double.MAX_VALUE);

        boolean aBeforeB = aMax.compareTo(bMin) < 0
                || (aMax.compareTo(bMin) == 0 && !a.isMaxInclusive() && !b.isMinInclusive());
        boolean bBeforeA = bMax.compareTo(aMin) < 0
                || (bMax.compareTo(aMin) == 0 && !b.isMaxInclusive() && !a.isMinInclusive());
        return !aBeforeB && !bBeforeA;
    }

    public record BandUpdate(
            BigDecimal minPh,
            BigDecimal maxPh,
            Boolean minInclusive,
            Boolean maxInclusive,
            String labelVi,
            String labelEn,
            String descriptionVi,
            String descriptionEn,
            BandSeverity severity,
            String colorToken,
            String iconName,
            Boolean triggersAlert,
            Integer sortOrder,
            Boolean active) {
    }
}
