package com.catcheck.architecture;

import com.catcheck.CatCheckApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void verifiesModularStructure() {
        ApplicationModules modules = ApplicationModules.of(CatCheckApplication.class);
        modules.verify();
    }
}
