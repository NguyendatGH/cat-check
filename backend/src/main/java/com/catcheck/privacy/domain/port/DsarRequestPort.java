package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.DsarRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.DsarStatus;

/**
 * Cổng quản lý {@code dsar_request} (p4 B5). Mọi thao tác tự phục vụ (toggle consent,
 * xuất dữ liệu, xoá tài khoản) đều tạo bản ghi ở đây (p15 REQ-DSAR-01).
 */
public interface DsarRequestPort {

    /** INSERT yêu cầu mới — {@code public_ref} sinh từ sequence {@code dsar_request_public_ref_seq}. */
    DsarRequest save(DsarRequest request);

    /**
     * Sinh mã {@code DSAR-2026-000123} tiếp theo (sequence + năm hiện tại). Gọi TRONG
     * transaction của {@link #save} để tránh hụt số khi rollback.
     */
    String nextPublicRef();

    /** Cập nhật trạng thái/SLA — không bao giờ sửa {@code received_at}, {@code ack_due_at}, {@code fulfil_due_at} (p4 B5). */
    void update(DsarRequest request);

    Optional<DsarRequest> findByPublicRef(String publicRef);

    Optional<DsarRequest> findById(UUID id);

    /**
     * Yêu cầu của user, mới nhất trước — {@code /privacy/requests} (cursor pagination,
     * p8 §8.1.4). Keyset theo {@code (received_at, id)}: {@code before} là
     * {@code received_at} của cursor, {@code beforeId} là {@code id} của cursor — hai
     * tham số cùng lúc để không lặp/nhảy khi nhiều dòng trùng {@code received_at}.
     */
    List<DsarRequest> findByUser(UUID userId, Instant before, UUID beforeId, int limit);

    /** Yêu cầu xuất gần nhất của user — kiểm giới hạn 1/24 giờ (p15 §15.4.5). */
    Optional<DsarRequest> findLatestExportRequest(UUID userId, Instant since);

    /** Đã có yêu cầu xoá đang mở chưa — partial unique index {@code uq_dsar_request_open_erase} (p4 B5). */
    boolean existsOpenEraseRequest(UUID userId);

    /** Danh sách quản trị đã lọc — không dùng cursor tự phục vụ của user. */
    List<DsarRequest> findForAdmin(DsarRequestType requestType, DsarStatus status, int offset, int limit);

    long countForAdmin(DsarRequestType requestType, DsarStatus status);
}
