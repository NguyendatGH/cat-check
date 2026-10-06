package com.catcheck.privacy.application.export;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DsarExportJobPort {
    DsarExportJob enqueue(UUID requestId, String downloadTokenHash);
    Optional<DsarExportJob> findByRequestId(UUID requestId);
    boolean claim(UUID requestId);
    void complete(UUID requestId, String storageKey, Instant expiresAt);
    void fail(UUID requestId, String errorCode);
    boolean claimDownload(UUID requestId, String downloadTokenHash, Instant now);

    /**
     * L62 / runbook R1 (p11 §11.13.4 — "link tải gói DSAR còn hạn" phải vô hiệu trong cùng
     * thao tác thu hồi phiên): hết hạn ngay mọi gói đã sẵn sàng và xoá hai hash token.
     *
     * <p>Cố ý <b>không</b> đặt {@code status = 'EXPIRED'}: {@code DsarExportCleanupJob} tìm
     * dòng {@code status='COMPLETED' AND expires_at<=now} để xoá file ZIP khỏi storage — đổi
     * trạng thái ở đây sẽ làm file chứa trọn bộ dữ liệu cá nhân nằm lại trên đĩa vĩnh viễn,
     * đúng thứ p15 §15.4.5 gọi là "rủi ro cao nhất trong toàn bộ bảng retention".</p>
     *
     * @return số gói vừa bị vô hiệu
     */
    int revokeAllDownloadLinks(Instant now);
}
