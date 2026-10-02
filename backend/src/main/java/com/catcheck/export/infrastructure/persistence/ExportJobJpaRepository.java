package com.catcheck.export.infrastructure.persistence;

import com.catcheck.export.domain.ExportJob;
import com.catcheck.export.domain.ExportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ExportJobJpaRepository extends JpaRepository<ExportJob, UUID> {

    boolean existsByDocumentCode(String documentCode);

    Optional<ExportJob> findFirstByUserIdAndStatusIn(UUID userId, List<ExportStatus> statuses);
}
