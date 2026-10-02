package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.Scan;
import com.catcheck.scan.domain.ScanStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository cho {@code scan}. Nằm ở {@code infrastructure.persistence} (R7).
 * Tầng application chỉ biết {@code scan.domain.port.ScanRepository} (R2).
 */
interface ScanJpaRepository extends JpaRepository<Scan, UUID> {

    Optional<Scan> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    List<Scan> findByCatIdAndDeletedAtIsNullOrderByCapturedAtDesc(UUID catId, Pageable pageable);

    List<Scan> findByCatIdAndStatusAndDeletedAtIsNullAndDisputedAtIsNullAndCapturedAtGreaterThanEqualOrderByCapturedAtDesc(
            UUID catId, ScanStatus status, Instant since, Pageable pageable);
}
