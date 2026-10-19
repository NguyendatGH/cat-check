package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.DsarRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.DsarStatus;

public interface DsarRequestPort {

    DsarRequest save(DsarRequest request);

    String nextPublicRef();

    void update(DsarRequest request);

    Optional<DsarRequest> findByPublicRef(String publicRef);

    Optional<DsarRequest> findById(UUID id);

    List<DsarRequest> findByUser(UUID userId, Instant before, UUID beforeId, int limit);

    Optional<DsarRequest> findLatestExportRequest(UUID userId, Instant since);

    boolean existsOpenEraseRequest(UUID userId);

    List<DsarRequest> findForAdmin(DsarRequestType requestType, DsarStatus status, int offset, int limit);

    long countForAdmin(DsarRequestType requestType, DsarStatus status);

    /**
     * Đếm toàn bộ DSAR (không phân trang) cho KPI admin: đang mở (chưa COMPLETED/REJECTED),
     * quá hạn (đang mở và coalesce(extended_to, fulfil_due_at) trước {@code now}), đã gia hạn (EXTENDED).
     */
    AdminSummary summarizeForAdmin(DsarRequestType requestType, Instant now);

    record AdminSummary(long open, long overdue, long extended) { }
}
