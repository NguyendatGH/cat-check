package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PackagePlanAdminView;
import com.catcheck.credit.domain.PackagePlanUpdate;

import java.util.List;
import java.util.Optional;

public interface PackagePlanPort {

    Optional<PackagePlan> findByCode(String code);

    List<PackagePlan> findAllActive();

    List<PackagePlanAdminView> findAllForAdmin(boolean includeInactive);

    Optional<PackagePlanAdminView> findForAdmin(String code);

    boolean update(String code, PackagePlanUpdate update);
}
