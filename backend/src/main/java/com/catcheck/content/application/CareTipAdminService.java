package com.catcheck.content.application;

import com.catcheck.content.api.CareTipChangedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CareTipAdminService {

    public static final int REASON_MIN_LENGTH = 10;

    private final CareTipRepository careTipRepository;
    private final UuidV7 uuidV7;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public CareTipAdminService(CareTipRepository careTipRepository, UuidV7 uuidV7, Clock clock,
                               ApplicationEventPublisher events) {
        this.careTipRepository = careTipRepository;
        this.uuidV7 = uuidV7;
        this.clock = clock;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public AdminPage<CareTip> listForAdmin(String locale, CareTipStatus status, int page, int size) {
        List<CareTip> items = careTipRepository.findAllForAdmin(locale, status, page, size);
        long total = careTipRepository.countForAdmin(locale, status);
        return new AdminPage<>(items, page, size, total);
    }

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

    public CareTip updateDraft(UUID tipId, String title, String summary, String bodyMd,
                               ClaimType claimType, String sourceReference, Integer sortWeight) {
        CareTip tip = require(tipId);
        if (!tip.isEditable()) {
            throw new ConflictException(ContentErrorCode.CONTENT_NOT_EDITABLE, tip.getStatus().name());
        }
        tip.revise(title, summary, bodyMd, claimType, sourceReference, sortWeight, clock.instant());
        return careTipRepository.save(tip);
    }

    public CareTip submitReview(UUID tipId) {
        CareTip tip = require(tipId);
        if (tip.getStatus() == CareTipStatus.PUBLISHED || tip.getStatus() == CareTipStatus.ARCHIVED) {
            throw new ConflictException(ContentErrorCode.CONTENT_INVALID_STATE,
                    tip.getStatus().name(), CareTipStatus.IN_REVIEW.name());
        }
        tip.submitReview(clock.instant());
        return careTipRepository.save(tip);
    }

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
        CareTip saved = careTipRepository.save(tip);
        events.publishEvent(new CareTipChangedEvent(saved.getId()));
        return saved;
    }

    public CareTip archive(UUID tipId) {
        CareTip tip = require(tipId);
        tip.archive(clock.instant());
        CareTip saved = careTipRepository.save(tip);
        events.publishEvent(new CareTipChangedEvent(saved.getId()));
        return saved;
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

    public record AdminPage<T>(List<T> items, int number, int size, long totalElements) {

        public int totalPages() {
            return size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        }

        public boolean hasMore() {
            return (long) (number + 1) * size < totalElements;
        }
    }
}
