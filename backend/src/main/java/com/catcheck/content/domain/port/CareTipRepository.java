package com.catcheck.content.domain.port;

import com.catcheck.content.domain.CareTip;
import com.catcheck.content.domain.CareTipCategory;
import com.catcheck.content.domain.CareTipKind;
import com.catcheck.content.domain.CareTipStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareTipRepository {

    Optional<CareTip> findById(UUID id);

    List<CareTip> findPublished(String locale, CareTipCategory category, CareTipKind kind, int limit);

    Optional<CareTip> findBySlugAndLocale(String slug, String locale);

    List<CareTip> findAllForAdmin(String locale, CareTipStatus status, int page, int size);

    long countForAdmin(String locale, CareTipStatus status);

    List<CareTip> findReviewQueue(int limit);

    boolean existsBySlugAndLocale(String slug, String locale);

    CareTip save(CareTip careTip);

    void softDeleteAll(Collection<UUID> ids, java.time.Instant now);
}
