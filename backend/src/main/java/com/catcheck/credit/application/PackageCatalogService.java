package com.catcheck.credit.application;

import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.port.PackagePlanPort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * F3 — danh mục gói đang bán ({@code GET /reference/packages}, p8 §8.4.6).
 *
 * <p>Không {@code @Transactional}: một SELECT trên bảng cấu hình, không có ghi và không cần
 * đọc nhất quán với bảng khác.</p>
 */
@Service
public class PackageCatalogService {

    private final PackagePlanPort packagePlanPort;

    public PackageCatalogService(PackagePlanPort packagePlanPort) {
        this.packagePlanPort = packagePlanPort;
    }

    public List<PackagePlan> listActivePlans() {
        return packagePlanPort.findAllActive();
    }
}
