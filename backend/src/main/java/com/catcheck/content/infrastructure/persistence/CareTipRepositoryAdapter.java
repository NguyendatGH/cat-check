package com.catcheck.content.infrastructure.persistence;

import com.catcheck.content.domain.CareTip;
import com.catcheck.content.domain.CareTipCategory;
import com.catcheck.content.domain.CareTipKind;
import com.catcheck.content.domain.CareTipStatus;
import com.catcheck.content.domain.port.CareTipRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bộ chuyển đổi từ cổng {@link CareTipRepository} sang Spring Data.
 *
 * <p>Lớp này là "keo dán" giữa domain và Spring Data: application chỉ cầm
 * {@link CareTipRepository}, nên sau này đổi sang JDBC hay JdbcTemplate thì chỉ sửa một chỗ.</p>
 */
@Repository
class CareTipRepositoryAdapter implements CareTipRepository {

    /**
     * p8 §8.1.4: {@code limit} mặc định 20, tối đa 100; vượt trần trả {@code 400 VALIDATION_FAILED}
     * chứ không âm thầm kẹp. Việc kiểm tra nằm ở controller, ở đây chỉ chặn phòng thủ.
     */
    private static final int MAX_LIMIT = 100;

    private final CareTipJpaRepository jpaRepository;

    CareTipRepositoryAdapter(CareTipJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<CareTip> findById(UUID id) {
        return jpaRepository.findById(id).filter(tip -> !tip.isDeleted());
    }

    @Override
    public List<CareTip> findPublished(String locale, CareTipCategory category, CareTipKind kind, int limit) {
        return jpaRepository.findPublished(locale, category, kind, PageRequest.of(0, clampLimit(limit)));
    }

    @Override
    public Optional<CareTip> findBySlugAndLocale(String slug, String locale) {
        return jpaRepository.findBySlugAndLocale(slug, locale);
    }

    @Override
    public List<CareTip> findAllForAdmin(String locale, CareTipStatus status, int page, int size) {
        return jpaRepository.findAllForAdmin(locale, status,
                PageRequest.of(Math.max(page, 0), clampLimit(size)));
    }

    @Override
    public long countForAdmin(String locale, CareTipStatus status) {
        return jpaRepository.countForAdmin(locale, status);
    }

    @Override
    public List<CareTip> findReviewQueue(int limit) {
        return jpaRepository.findReviewQueue(PageRequest.of(0, clampLimit(limit)));
    }

    @Override
    public boolean existsBySlugAndLocale(String slug, String locale) {
        return jpaRepository.existsBySlugAndLocale(slug, locale);
    }

    @Override
    public CareTip save(CareTip careTip) {
        return jpaRepository.save(careTip);
    }

    @Override
    public void softDeleteAll(Collection<UUID> ids, Instant now) {
        if (!ids.isEmpty()) {
            jpaRepository.softDeleteAll(List.copyOf(ids), now);
        }
    }

    private int clampLimit(int limit) {
        return Math.clamp(limit, 1, MAX_LIMIT);
    }
}
