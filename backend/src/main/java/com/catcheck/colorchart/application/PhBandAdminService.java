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

/**
 * Quản trị dải phân loại pH — L36, L37 (p8 §8.4.12).
 *
 * <p>Ràng buộc bắt buộc (p4 D7, I9): các dải {@code active} phải <b>phủ kín trục pH, không chồng
 * lấn</b>. Kiểm ở service khi lưu — không ép được bằng {@code CHECK} đơn dòng.
 *
 * <p>Sửa ngưỡng {@code ph_min}/{@code ph_max} của một dải đang {@code active} là thao tác đổi ngưỡng
 * y học — p14 §14.5 yêu cầu step-up re-auth + reason. Việc đó thuộc tầng web (W3 nối SecurityConfig),
 * ở đây chỉ đảm bảo tính toàn vẹn dữ liệu.
 */
@Service
public class PhBandAdminService {

    private final PhClassificationBandRepository bandRepository;
    private final Clock clock;

    public PhBandAdminService(PhClassificationBandRepository bandRepository, Clock clock) {
        this.bandRepository = bandRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ L36

    @Transactional(readOnly = true)
    public List<PhClassificationBand> listBands() {
        return bandRepository.findGlobalActiveOrderBySortOrder();
    }

    // ------------------------------------------------------------------ L37

    /**
     * Sửa một dải phân loại theo {@code code} (L37).
     *
     * <p>Không cho sửa {@code code} — nó là danh tính nghiệp vụ xuất hiện trong
     * {@code scan_analysis.classification} và trong i18n key. Đổi code = phá vỡ lịch sử.
     */
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

    /**
     * Kiểm tra phủ kín + không chồng lấn trên trục pH (I9).
     *
     * <p>Chỉ kiểm các dải {@code active} cùng nhóm (toàn cục hoặc cùng {@code chart_id}). Dải
     * {@code INCONCLUSIVE} không có khoảng nên không tham gia phủ.
     */
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

    /**
     * Cập nhật một dải (L37). Vắng field = không đổi; {@code null} tường minh trong record này
     * nghĩa là không đổi (khác merge-patch ở tầng API).
     */
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
