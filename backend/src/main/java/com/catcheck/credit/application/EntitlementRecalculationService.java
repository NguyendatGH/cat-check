package com.catcheck.credit.application;

import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PlanFeatures;
import com.catcheck.credit.domain.PlanTier;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tính lại {@code user_entitlement} từ các lô credit user đang có (bất biến I28, p5 R5).
 *
 * <p><b>Tách ra khỏi {@code ActivateCreditCodeService} vì nay có HAI đường tạo lô credit:</b>
 * người dùng đổi mã (p8 H1) và admin cấp tay (p8 L10 / p14 §14.4.4). Nếu đường thứ hai không
 * tính lại entitlement thì credit admin vừa cấp sẽ <b>không dùng được</b>: {@code consume} đòi
 * {@code hasWriteAccessAt(now)} và {@code write_access_until} chỉ được cập nhật ở chỗ này — một
 * tài khoản có số dư dương mà mọi lần quét đều trả {@code 403 WRITE_ACCESS_EXPIRED}.</p>
 *
 * <p>Điểm mấu chốt của p5 R5: quyền <b>đọc</b> giữ vĩnh viễn — user từng mua MULTI rồi hạ xuống
 * MINI vẫn đọc được lịch sử và xuất PDF; chỉ quyền <b>tạo mới</b> bị chặn theo
 * {@code writeAccessUntil}. Vì vậy quét <b>TẤT CẢ</b> lô (kể cả lô đã đóng) chứ không chỉ lô còn
 * sống.</p>
 */
@Service
public class EntitlementRecalculationService {

    private final UserEntitlementPort userEntitlementPort;
    private final CreditBatchPort creditBatchPort;
    private final PackagePlanPort packagePlanPort;

    public EntitlementRecalculationService(UserEntitlementPort userEntitlementPort,
                                            CreditBatchPort creditBatchPort,
                                            PackagePlanPort packagePlanPort) {
        this.userEntitlementPort = userEntitlementPort;
        this.creditBatchPort = creditBatchPort;
        this.packagePlanPort = packagePlanPort;
    }

    /**
     * <b>{@code Propagation.MANDATORY} là cố ý</b>, cùng lý do như
     * {@code FefoCreditConsumptionService#consume}: entitlement phải cùng số phận với lô credit
     * vừa ghi. Nếu để {@code REQUIRED} mà ai đó gọi ngoài transaction thì entitlement được commit
     * trong khi lô credit rollback — một tài khoản có quyền ghi mà không có credit nào.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Entitlement recalculate(UUID userId, Instant now) {
        Entitlement current = userEntitlementPort.findOrDefault(userId, now);

        String highestCode = current.highestPackage();
        for (String packageCode : creditBatchPort.findAllBatches(userId).stream()
                .map(batch -> batch.packageCode())
                .filter(Objects::nonNull)
                .toList()) {
            highestCode = PlanTier.isHigher(packageCode, highestCode) ? packageCode : highestCode;
        }

        PlanFeatures features = PlanFeatures.none();
        Integer maxCatProfiles = null;
        if (highestCode != null) {
            Optional<PackagePlan> plan = packagePlanPort.findByCode(highestCode);
            if (plan.isPresent()) {
                features = plan.get().features();
                maxCatProfiles = plan.get().maxCatProfiles();
            }
        }

        // I28: write_access_until = MAX(expires_at) của các lô ĐÃ KÍCH HOẠT. Tính ở đây thay vì
        // để job cập nhật, để quyền ghi có hiệu lực ngay ở lần gọi kế tiếp (p8 H4: không cache
        // quyền trong phiên).
        Instant writeAccessUntil = userEntitlementPort.maxActivatedBatchExpiry(userId).orElse(null);

        Entitlement refreshed = new Entitlement(
                userId, highestCode, maxCatProfiles, features, writeAccessUntil,
                current.trialScansUsed(), now);
        userEntitlementPort.save(refreshed);
        return refreshed;
    }
}
