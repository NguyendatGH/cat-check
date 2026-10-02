package com.catcheck.credit.application;

import com.catcheck.credit.api.EntitlementQuery;
import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PlanFeature;
import com.catcheck.credit.domain.PlanTier;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Hiện thực {@link EntitlementQuery} — {@code GET /api/v1/entitlements/me} (p8 H4) và mọi
 * kiểm tra quyền mà module khác gọi.
 *
 * <p>Không có {@code @Transactional}: các phép kiểm tra ở đây đều là một câu đọc đơn lẻ, đọc ở
 * READ COMMITTED là đủ (p8 §8.3.1 bước 4c kiểm tra quyền trước khi tốn CPU, không cần tính nhất
 * quán chặt). Quan trọng hơn: <b>không khoá dòng nào</b> — khoá entitlement sẽ biến một lời gọi
 * kiểm tra quyền thành điểm tranh chấp trên mọi luồng ghi credit.</p>
 */
@Service
public class EntitlementService implements EntitlementQuery {

    private final UserEntitlementPort userEntitlementPort;
    private final PackagePlanPort packagePlanPort;
    private final Clock clock;

    public EntitlementService(
            UserEntitlementPort userEntitlementPort, PackagePlanPort packagePlanPort, Clock clock) {
        this.userEntitlementPort = userEntitlementPort;
        this.packagePlanPort = packagePlanPort;
        this.clock = clock;
    }

    @Override
    public Entitlement snapshot(UUID userId) {
        return userEntitlementPort.findOrDefault(userId, clock.instant());
    }

    @Override
    public Integer maxCatProfiles(UUID userId) {
        return snapshot(userId).maxCatProfiles();
    }

    @Override
    public boolean isFeatureEnabled(UUID userId, PlanFeature feature) {
        return snapshot(userId).isFeatureEnabled(feature);
    }

    @Override
    public boolean hasWriteAccess(UUID userId) {
        return snapshot(userId).hasWriteAccessAt(clock.instant());
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code maxCatProfiles == null} nghĩa là KHÔNG giới hạn (gói MULTI / CARE_BOX, p5 §5.3)
     * → luôn cho phép. Nếu có hạn mức thì chỉ áp dụng lúc TẠO MỚI, không hồi tố: hạ gói không
     * xoá hồ sơ cũ (p5 R5, bất biến I27).</p>
     *
     * <p>{@code requiredPackage} là gói thấp nhất còn đang bán mà có hạn mức cao hơn — chỉ để
     * dựng CTA. Không có gói nào đủ thì trả {@code null}: màn hình hiện "cần liên hệ" thay vì
     * gợi ý mua một gói cũng không giải quyết được.</p>
     */
    @Override
    public CatProfileAllowance checkCatProfileAllowance(UUID userId, int currentCount) {
        Entitlement entitlement = snapshot(userId);
        Integer max = entitlement.maxCatProfiles();
        if (max == null) {
            return new CatProfileAllowance(true, currentCount, null, entitlement.highestPackage(), null);
        }
        boolean allowed = currentCount < max;
        return new CatProfileAllowance(
                allowed, currentCount, max, entitlement.highestPackage(),
                allowed ? null : lowestPackageWithHigherLimit(max));
    }

    /**
     * Gói thấp nhất theo thứ tự {@link PlanTier} còn đang bán mà có hạn mức hồ sơ mèo cao hơn
     * {@code currentMax} — chỉ để dựng CTA, không phải quy tắc nghiệp vụ. Vì vậy hạn mức đọc từ
     * {@code package_plan} thật chứ không hard-code: admin thêm gói mới thì CTA tự đúng mà không
     * phải sửa code.
     *
     * <p>Gói không còn bán ({@code active = false}) bị bỏ qua: gợi ý mua một gói không còn bán là
     * gợi ý sai.</p>
     */
    private String lowestPackageWithHigherLimit(int currentMax) {
        for (PlanTier tier : PlanTier.values()) {
            Optional<PackagePlan> plan = packagePlanPort.findByCode(tier.code());
            if (plan.isPresent()
                    && plan.get().active()
                    && plan.get().maxCatProfiles() != null
                    && plan.get().maxCatProfiles() > currentMax) {
                return tier.code();
            }
        }
        return null;
    }

}
