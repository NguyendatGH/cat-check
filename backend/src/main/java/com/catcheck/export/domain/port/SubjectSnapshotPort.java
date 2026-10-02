package com.catcheck.export.domain.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc-only thông tin hiển thị trang bìa PDF (tên mèo/chủ nuôi, không phải toàn bộ hồ sơ) —
 * hiện thực bằng JDBC thuần đọc trực tiếp {@code cat}/{@code app_user}, cùng judgment call với
 * {@code scan.domain.port.CatOwnershipPort} (xem {@code docs/handovers/A6.md}): hai module đó
 * chưa công bố named interface "api" có query port cross-module ở M0.
 */
public interface SubjectSnapshotPort {

    Optional<CatProfile> findCatProfile(UUID catId);

    Optional<OwnerProfile> findOwnerProfile(UUID userId);

    record CatProfile(
            UUID catId,
            UUID ownerId,
            String name,
            String breedLabel,
            String sex,
            LocalDate birthDate,
            Integer approxAgeMonths,
            BigDecimal weightKg,
            String avatarStorageKey
    ) {
    }

    record OwnerProfile(UUID userId, String fullName, String locale, String timezone) {
    }
}
