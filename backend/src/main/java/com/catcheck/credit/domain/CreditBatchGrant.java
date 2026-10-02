package com.catcheck.credit.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Lô credit MỚI cần ghi — sinh ra khi một mã kích hoạt được đổi (p5 R1).
 *
 * <p>Tách khỏi {@link CreditBatchSnapshot} vì hai việc khác nhau: snapshot là ảnh chụp <b>đọc</b>
 * tối thiểu cho thuật toán FEFO, còn đây là <b>lệnh tạo</b> mang đủ cột để {@code INSERT}.
 * Lý do tách: FEFO không cần biết {@code package_version} hay {@code initial_amount}, và đỡ
 * phải điền những trường không liên quan mỗi lần khoá dòng.</p>
 *
 * <p>Toàn bộ cấu hình gói được <b>snapshot</b> vào đây: {@code package_code} +
 * {@code package_version} + {@code initial_amount}. Admin sửa {@code package_plan} sau này
 * không được hồi tố lô đã phát hành (p17 C12).</p>
 *
 * @param id               UUID v7
 * @param userId           chủ lô
 * @param activationCodeId mã đã đổi, {@code null} khi lô do admin cấp tay (M6)
 * @param packageCode      mã gói đã snapshot
 * @param packageVersion   version cấu hình gói tại lúc kích hoạt
 * @param creditAmount     số credit được cấp = {@code initial_amount}
 * @param activatedAt      lúc kích hoạt — mốc bắt đầu tính hạn
 * @param expiresAt        hạn credit, {@code activatedAt + credit_validity_days} (p5 R1)
 */
public record CreditBatchGrant(
        UUID id,
        UUID userId,
        UUID activationCodeId,
        String packageCode,
        int packageVersion,
        int creditAmount,
        Instant activatedAt,
        Instant expiresAt
) {

    public CreditBatchGrant {
        if (id == null || userId == null || packageCode == null || activatedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("creditBatchGrant thiếu trường bắt buộc");
        }
        if (creditAmount <= 0) {
            throw new IllegalArgumentException("creditBatchGrant.creditAmount phải > 0: " + id);
        }
        if (!expiresAt.isAfter(activatedAt)) {
            throw new IllegalArgumentException(
                    "creditBatchGrant cần expiresAt > activatedAt (ck_credit_batch_expiry): " + id);
        }
    }

    public static CreditBatchGrant of(
            UUID id, UUID userId, UUID activationCodeId, PackagePlan plan, Instant activatedAt) {
        return new CreditBatchGrant(
                id,
                userId,
                activationCodeId,
                plan.code(),
                plan.version(),
                plan.creditAmount(),
                activatedAt,
                activatedAt.plus(Duration.ofDays(plan.creditValidityDays())));
    }

    /** Lô mới luôn mở: {@code remaining_amount = initial_amount} (p5 R1). */
    public CreditBatchSnapshot toSnapshot() {
        return new CreditBatchSnapshot(id, expiresAt, creditAmount, creditAmount);
    }
}
