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
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class PhBandAdminService {

    // PhBandClassifier fails without these: IN_RANGE drives nearBoundary, INCONCLUSIVE low-confidence results.
    private static final Set<String> REQUIRED_CODES = Set.of("IN_RANGE", "INCONCLUSIVE");

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
        if (band.getChartId() == null) {
            // The scan classifier reads the global set: it needs IN_RANGE/INCONCLUSIVE and full pH coverage.
            validateGlobalSetUsable(band);
        }
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

    private void validateGlobalSetUsable(PhClassificationBand updated) {
        if (REQUIRED_CODES.contains(updated.getCode()) && !updated.isActive()) {
            throw new BusinessRuleException(ColorChartErrorCode.PH_BAND_REQUIRED, updated.getCode());
        }

        List<PhClassificationBand> ranged = new ArrayList<>();
        for (PhClassificationBand band : bandRepository.findGlobalActiveOrderBySortOrder()) {
            if (!band.getId().equals(updated.getId()) && isRanged(band)) {
                ranged.add(band);
            }
        }
        if (updated.isActive() && isRanged(updated)) {
            ranged.add(updated);
        }
        // Lower-unbounded first; overlaps are rejected separately, so only gaps are checked here.
        ranged.sort(Comparator.comparing(PhClassificationBand::getMinPh,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        if (ranged.isEmpty()) {
            throw new BusinessRuleException(ColorChartErrorCode.PH_BAND_COVERAGE_GAP, "-");
        }
        if (ranged.getFirst().getMinPh() != null) {
            throw new BusinessRuleException(ColorChartErrorCode.PH_BAND_COVERAGE_GAP,
                    ranged.getFirst().getMinPh().toPlainString());
        }
        if (ranged.getLast().getMaxPh() != null) {
            throw new BusinessRuleException(ColorChartErrorCode.PH_BAND_COVERAGE_GAP,
                    ranged.getLast().getMaxPh().toPlainString());
        }
        for (int i = 1; i < ranged.size(); i++) {
            PhClassificationBand prev = ranged.get(i - 1);
            PhClassificationBand next = ranged.get(i);
            if (prev.getMaxPh() == null || next.getMinPh() == null) {
                continue;
            }
            int cmp = prev.getMaxPh().compareTo(next.getMinPh());
            if (cmp < 0 || (cmp == 0 && !prev.isMaxInclusive() && !next.isMinInclusive())) {
                throw new BusinessRuleException(ColorChartErrorCode.PH_BAND_COVERAGE_GAP,
                        prev.getMaxPh().toPlainString());
            }
        }
    }

    private static boolean isRanged(PhClassificationBand band) {
        return band.getMinPh() != null || band.getMaxPh() != null;
    }

    private boolean overlaps(PhClassificationBand a, PhClassificationBand b) {
        BigDecimal aMin = a.getMinPh() != null ? a.getMinPh() : BigDecimal.valueOf(-Double.MAX_VALUE);
        BigDecimal aMax = a.getMaxPh() != null ? a.getMaxPh() : BigDecimal.valueOf(Double.MAX_VALUE);
        BigDecimal bMin = b.getMinPh() != null ? b.getMinPh() : BigDecimal.valueOf(-Double.MAX_VALUE);
        BigDecimal bMax = b.getMaxPh() != null ? b.getMaxPh() : BigDecimal.valueOf(Double.MAX_VALUE);

        // Touching ends overlap only when both include the shared point (seed: [6.0,6.3) + [6.3,6.6]).
        boolean aBeforeB = aMax.compareTo(bMin) < 0
                || (aMax.compareTo(bMin) == 0 && (!a.isMaxInclusive() || !b.isMinInclusive()));
        boolean bBeforeA = bMax.compareTo(aMin) < 0
                || (bMax.compareTo(aMin) == 0 && (!b.isMaxInclusive() || !a.isMinInclusive()));
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
