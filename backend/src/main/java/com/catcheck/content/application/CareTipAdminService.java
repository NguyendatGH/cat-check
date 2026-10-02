package com.catcheck.content.application;

import com.catcheck.content.api.ContentErrorCode;
import com.catcheck.content.domain.CareTip;
import com.catcheck.content.domain.CareTipCategory;
import com.catcheck.content.domain.CareTipKind;
import com.catcheck.content.domain.CareTipStatus;
import com.catcheck.content.domain.ClaimType;
import com.catcheck.content.domain.port.CareTipRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Vòng đời duyệt nội dung cho quản trị (L40–L45).
 *
 * <p>Chuỗi trạng thái đúng theo Q20/Q21: {@code DRAFT → IN_REVIEW → PUBLISHED → ARCHIVED}, và
 * không đường nào bỏ qua bước duyệt. Hai chốt chặn nghiệp vụ ở {@link #publish}:
 * bài có tuyên bố phải có nguồn (I31), và người soạn không được tự duyệt bài của mình.</p>
 *
 * <p>Không ghi {@code audit_log} trực tiếp: đó là bảng của module {@code audit} (p7 §7.2.3) và
 * {@code content} chỉ được phụ thuộc {@code audit}, chưa có cổng công khai để ghi. Xem
 * {@code docs/handovers/A3.md} mục "Còn lại".</p>
 */
@Service
@Transactional
public class CareTipAdminService {

    /** p8 §8.3.2 {@code Rsn} + p15 REQ-AUD-03 — lý do tối thiểu khi thao tác nhạy cảm. */
    public static final int REASON_MIN_LENGTH = 10;

    private final CareTipRepository careTipRepository;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public CareTipAdminService(CareTipRepository careTipRepository, UuidV7 uuidV7, Clock clock) {
        this.careTipRepository = careTipRepository;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /** L40 — danh sách mọi trạng thái, phân trang kiểu offset. */
    @Transactional(readOnly = true)
    public AdminPage<CareTip> listForAdmin(String locale, CareTipStatus status, int page, int size) {
        List<CareTip> items = careTipRepository.findAllForAdmin(locale, status, page, size);
        long total = careTipRepository.countForAdmin(locale, status);
        return new AdminPage<>(items, page, size, total);
    }

    /** L41 — tạo bản nháp. */
    public CareTip createDraft(String slug, String locale, CareTipKind kind, CareTipCategory category,
                               String title, ClaimType claimType, UUID authorId) {
        if (careTipRepository.existsBySlugAndLocale(slug, locale)) {
            throw new ConflictException(ContentErrorCode.CONTENT_SLUG_TAKEN, slug, locale);
        }
        Instant now = clock.instant();
        CareTip tip = CareTip.createDraft(
                uuidV7.generate(), slug, uuidV7.generate(), locale, kind, title, claimType, authorId, now);
        tip.applyCategory(category, now);
        return careTipRepository.save(tip);
    }

    /** L42 — sửa bản nháp (kể cả {@code claimType} và {@code sourceReference}, H9.3). */
    public CareTip updateDraft(UUID tipId, String title, String summary, String bodyMd,
                               ClaimType claimType, String sourceReference, Integer sortWeight) {
        CareTip tip = require(tipId);
        if (!tip.isEditable()) {
            throw new ConflictException(ContentErrorCode.CONTENT_NOT_EDITABLE, tip.getStatus().name());
        }
        tip.revise(title, summary, bodyMd, claimType, sourceReference, sortWeight, clock.instant());
        return careTipRepository.save(tip);
    }

    /** L43 — gửi duyệt. */
    public CareTip submitReview(UUID tipId) {
        CareTip tip = require(tipId);
        if (tip.getStatus() == CareTipStatus.PUBLISHED || tip.getStatus() == CareTipStatus.ARCHIVED) {
            throw new ConflictException(ContentErrorCode.CONTENT_INVALID_STATE,
                    tip.getStatus().name(), CareTipStatus.IN_REVIEW.name());
        }
        tip.submitReview(clock.instant());
        return careTipRepository.save(tip);
    }

    /**
     * L44 — công bố.
     *
     * @param reason bắt buộc dài ≥ {@value #REASON_MIN_LENGTH} ký tự
     * @throws ConflictException {@code CONTENT_SELF_APPROVAL_FORBIDDEN} khi người duyệt chính là
     *                            người soạn — p14 §14.3.5 nêu rõ điểm này vì bài có tuyên bố y khoá
     *                            cần hai người.
     */
    public CareTip publish(UUID tipId, UUID reviewerId, String reason) {
        requireReason(reason);
        CareTip tip = require(tipId);
        if (tip.getStatus() != CareTipStatus.IN_REVIEW) {
            throw new ConflictException(ContentErrorCode.CONTENT_INVALID_STATE,
                    tip.getStatus().name(), CareTipStatus.PUBLISHED.name());
        }
        if (tip.getAuthorId() != null && tip.getAuthorId().equals(reviewerId)) {
            throw new ConflictException(ContentErrorCode.CONTENT_SELF_APPROVAL_FORBIDDEN, tip.getAuthorId());
        }
        if (tip.getClaimType().requiresSourceReference() && tip.nonBlankSourceReference().isEmpty()) {
            throw new BusinessRuleException(ContentErrorCode.CONTENT_SOURCE_REQUIRED, tip.getClaimType().name());
        }
        tip.publish(reviewerId, reason, clock.instant());
        return careTipRepository.save(tip);
    }

    /** L45 — gỡ khỏi hiển thị. Bài đã công bố vẫn còn để tra cứu, chỉ không hiện nữa. */
    public CareTip archive(UUID tipId) {
        CareTip tip = require(tipId);
        tip.archive(clock.instant());
        return careTipRepository.save(tip);
    }

    private CareTip require(UUID tipId) {
        return careTipRepository.findById(tipId)
                .orElseThrow(() -> new NotFoundException(ContentErrorCode.CONTENT_NOT_FOUND));
    }

    private void requireReason(String reason) {
        if (reason == null || reason.trim().length() < REASON_MIN_LENGTH) {
            throw new BusinessRuleException(ContentErrorCode.REASON_REQUIRED, REASON_MIN_LENGTH);
        }
    }

    /**
     * Trang kết quả kiểu offset cho admin (p8 §8.1.4). Không phải record của {@code api.dto} vì tầng
     * application trả về kiểu domain, chuyển sang DTO ở {@code api}.
     */
    public record AdminPage<T>(List<T> items, int number, int size, long totalElements) {

        public int totalPages() {
            return size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        }

        public boolean hasMore() {
            return (long) (number + 1) * size < totalElements;
        }
    }
}
