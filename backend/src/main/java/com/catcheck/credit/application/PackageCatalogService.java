package com.catcheck.credit.application;

import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.port.PackagePlanPort;
import org.springframework.stereotype.Service;

import java.util.List;

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
