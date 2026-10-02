package com.catcheck.content.application;

import com.catcheck.content.api.ContentErrorCode;
import com.catcheck.content.domain.CareTip;
import com.catcheck.content.domain.CareTipCategory;
import com.catcheck.content.domain.CareTipKind;
import com.catcheck.content.domain.port.CareTipRepository;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Đọc nội dung chăm sóc cho người dùng (F7, F8) — không cần đăng nhập.
 *
 * <p>Mọi truy vấn công khai đều lọc {@code status = PUBLISHED} và {@code deleted_at IS NULL}.
 * Bài nháp của admin KHÔNG bao giờ lọt ra ngoài, kể cả khi client đoán đúng slug.</p>
 */
@Service
@Transactional(readOnly = true)
public class CareTipQueryService {

    private final CareTipRepository careTipRepository;

    public CareTipQueryService(CareTipRepository careTipRepository) {
        this.careTipRepository = careTipRepository;
    }

    /** F7 — {@code GET /care-tips}, lọc {@code ?category=} và {@code ?kind=}. */
    public List<CareTip> listPublished(String locale, CareTipCategory category, CareTipKind kind, int limit) {
        return careTipRepository.findPublished(locale, category, kind, limit);
    }

    /**
     * F8 — {@code GET /care-tips/{slug}}.
     *
     * <p>Trả {@code CONTENT_NOT_FOUND} khi bài tồn tại nhưng chưa {@code PUBLISHED}: đây là cùng một
     * mã với "không tồn tại" (p8 §8.2.5 — không tiết lộ trạng thái nội bộ qua khác biệt mã lỗi).</p>
     */
    public CareTip getPublishedBySlug(String slug, String locale) {
        return careTipRepository.findBySlugAndLocale(slug, locale)
                .filter(CareTip::isPublished)
                .orElseThrow(() -> new NotFoundException(ContentErrorCode.CONTENT_NOT_FOUND));
    }
}
