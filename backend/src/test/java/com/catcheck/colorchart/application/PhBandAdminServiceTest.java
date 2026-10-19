package com.catcheck.colorchart.application;

import com.catcheck.colorchart.api.ColorChartErrorCode;
import com.catcheck.colorchart.domain.BandSeverity;
import com.catcheck.colorchart.domain.PhClassificationBand;
import com.catcheck.colorchart.domain.port.PhClassificationBandRepository;
import com.catcheck.shared.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhBandAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    private final FakeBandRepository bands = new FakeBandRepository();
    private final PhBandAdminService service =
            new PhBandAdminService(bands, Clock.fixed(NOW, ZoneOffset.UTC));

    PhBandAdminServiceTest() {
        // Same global set as the V9 seed.
        bands.add("LOW", null, "6.0", true, false, 1);
        bands.add("SLIGHTLY_LOW", "6.0", "6.3", true, false, 2);
        bands.add("IN_RANGE", "6.3", "6.6", true, true, 3);
        bands.add("SLIGHTLY_HIGH", "6.6", "7.0", false, true, 4);
        bands.add("HIGH", "7.0", null, false, true, 5);
        bands.add("INCONCLUSIVE", null, null, true, true, 6);
    }

    @Test
    void labelEditOnTheSeedSetIsAccepted() {
        PhClassificationBand updated = service.updateBand("IN_RANGE", update(null, null, null, null));

        assertEquals("Nhan moi", updated.getLabelVi());
    }

    @Test
    void deactivatingInRangeIsRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.updateBand("IN_RANGE", update(null, null, null, false)));

        assertEquals(ColorChartErrorCode.PH_BAND_REQUIRED, ex.errorCode());
    }

    @Test
    void deactivatingInconclusiveIsRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.updateBand("INCONCLUSIVE", update(null, null, null, false)));

        assertEquals(ColorChartErrorCode.PH_BAND_REQUIRED, ex.errorCode());
    }

    @Test
    void shrinkingABandSoAGapOpensIsRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.updateBand("SLIGHTLY_LOW", update(null, "6.2", null, null)));

        assertEquals(ColorChartErrorCode.PH_BAND_COVERAGE_GAP, ex.errorCode());
    }

    @Test
    void deactivatingAnOuterBandLeavesTheAxisUncoveredAndIsRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.updateBand("HIGH", update(null, null, null, false)));

        assertEquals(ColorChartErrorCode.PH_BAND_COVERAGE_GAP, ex.errorCode());
    }

    @Test
    void makingBothSidesOfABoundaryExclusiveIsRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.updateBand("IN_RANGE", update(null, null, false, null)));

        assertEquals(ColorChartErrorCode.PH_BAND_COVERAGE_GAP, ex.errorCode());
    }

    @Test
    void overlapIsStillReportedAsOverlap() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.updateBand("IN_RANGE", update("6.2", null, null, null)));

        assertEquals(ColorChartErrorCode.PH_BAND_OVERLAP, ex.errorCode());
    }

    @Test
    void rejectedEditIsNotSaved() {
        assertThrows(BusinessRuleException.class,
                () -> service.updateBand("HIGH", update(null, null, null, false)));

        assertFalse(bands.saved);
    }

    private static PhBandAdminService.BandUpdate update(String minPh, String maxPh, Boolean minInclusive,
                                                        Boolean active) {
        return new PhBandAdminService.BandUpdate(
                minPh == null ? null : new BigDecimal(minPh),
                maxPh == null ? null : new BigDecimal(maxPh),
                minInclusive, null, "Nhan moi", null, null, null, null, null, null, null, null, active);
    }

    private static final class FakeBandRepository implements PhClassificationBandRepository {

        private final Map<UUID, PhClassificationBand> store = new LinkedHashMap<>();
        private boolean saved;

        void add(String code, String min, String max, boolean minIncl, boolean maxIncl, int sortOrder) {
            UUID id = UUID.randomUUID();
            store.put(id, new PhClassificationBand(id, null, code,
                    min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max),
                    minIncl, maxIncl, code, code, null, null, BandSeverity.NORMAL, "color-ph-normal",
                    "check-circle", false, sortOrder, true, NOW, NOW));
        }

        @Override
        public List<PhClassificationBand> findGlobalActiveOrderBySortOrder() {
            return store.values().stream()
                    .filter(b -> b.getChartId() == null && b.isActive())
                    .sorted(Comparator.comparingInt(PhClassificationBand::getSortOrder))
                    .toList();
        }

        @Override
        public List<PhClassificationBand> findByChartIdOrderBySortOrder(UUID chartId) {
            return List.of();
        }

        @Override
        public Optional<PhClassificationBand> findByCode(String code) {
            return store.values().stream().filter(b -> b.getCode().equals(code)).findFirst();
        }

        @Override
        public Optional<PhClassificationBand> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public PhClassificationBand save(PhClassificationBand band) {
            saved = true;
            store.put(band.getId(), band);
            return band;
        }
    }
}
