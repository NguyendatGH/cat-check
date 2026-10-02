package com.catcheck.credit.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Quyền tính năng hiện tại của một user — dòng {@code user_entitlement}.
 *
 * <p>Đây là bảng DẪN XUẤT: tính lại được từ {@code credit_batch} + {@code package_plan}, nên
 * {@code recomputedAt} tồn tại để biết lần cuối ai tính lúc nào — không có nó thì không phát
 * hiện được lúc bảng lệch (p4 group E, bất biến I28).</p>
 *
 * <p><b>Quan trọng — p5 R5:</b> entitlement <b>không</b> hết hạn cùng credit.
 * {@code highestPackage} là gói cao nhất user <b>từng</b> kích hoạt, và {@code features} là
 * snapshot hợp nhất của gói đó. {@code writeAccessUntil} là cửa sổ riêng cho quyền GHI/TẠO
 * MỚI (scan mới, tạo hồ sơ mèo vượt hạn mức, đặt reminder mới) và bằng
 * {@code max(expires_at)} của các lô đã kích hoạt (bất biến I28).</p>
 *
 * @param userId             chủ tài khoản (PK)
 * @param highestPackage     mã gói cao nhất từng kích hoạt, null nếu chưa kích hoạt gói nào
 * @param maxCatProfiles     số hồ sơ mèo tối đa, null = không giới hạn
 * @param features           snapshot hợp nhất — quyền ĐỌC, giữ vĩnh viễn
 * @param writeAccessUntil   hết cửa sổ quyền ghi/tạo mới, null nếu không còn gói hiệu lực
 * @param trialScansUsed     số lượt trial đã dùng (p5 R6, quyết định #12)
 * @param recomputedAt       lần tính lại gần nhất
 */
public record Entitlement(
        UUID userId,
        String highestPackage,
        Integer maxCatProfiles,
        PlanFeatures features,
        Instant writeAccessUntil,
        int trialScansUsed,
        Instant recomputedAt
) {

    public Entitlement {
        if (userId == null) {
            throw new IllegalArgumentException("entitlement.userId phải có giá trị");
        }
        if (features == null) {
            features = PlanFeatures.none();
        }
        if (trialScansUsed < 0) {
            throw new IllegalArgumentException("entitlement.trialScansUsed không được âm: " + userId);
        }
    }

    /**
     * Dòng mặc định cho user chưa kích hoạt gói nào: quyền bằng {@link PlanFeatures#none()},
     * chưa dùng lượt trial nào, không có cửa sổ quyền ghi.
     *
     * <p>Chỉ dùng cho lúc ĐỌC khi dòng chưa tồn tại — cùng trạng thái nghiệp vụ với một dòng mặc
     * định đã lưu, nên không cần ghi xuống DB chỉ để trả lời một câu hỏi đọc. Dòng thật được tạo
     * ở lần ghi đầu tiên.</p>
     */
    public static Entitlement defaults(UUID userId, Instant now) {
        return new Entitlement(userId, null, null, PlanFeatures.none(), null, 0, now);
    }

    /** Quyền ghi/tạo mới còn mở tại thời điểm {@code now} hay không (p5 R5). */
    public boolean hasWriteAccessAt(Instant now) {
        return writeAccessUntil != null && writeAccessUntil.isAfter(now);
    }

    /** Tính năng có mở hay không — quyền đọc, không phụ thuộc {@code writeAccessUntil}. */
    public boolean isFeatureEnabled(PlanFeature feature) {
        return features.isEnabled(feature);
    }
}
