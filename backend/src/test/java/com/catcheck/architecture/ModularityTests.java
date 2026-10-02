package com.catcheck.architecture;

import com.catcheck.CatCheckApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Xác thực toàn bộ cấu trúc module Spring Modulith: mỗi {@code allowedDependencies} khai ở
 * từng {@code package-info.java} phải trỏ tới module/named-interface có thật, và không có
 * class nào phụ thuộc module khác ngoài danh sách cho phép. {@link ApplicationModules#of}
 * cũng tự in ra "Documentation" (dùng để sinh tài liệu module sau này) khi cần.
 */
class ModularityTests {

    @Test
    void verifiesModularStructure() {
        ApplicationModules modules = ApplicationModules.of(CatCheckApplication.class);
        modules.verify();
    }
}
