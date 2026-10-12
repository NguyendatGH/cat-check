package com.catcheck.architecture;

import com.tngtech.archunit.core.domain.JavaClass;

final class ArchUnitSupport {

    private static final String BASE_PACKAGE = "com.catcheck";

    private ArchUnitSupport() {
    }

    static String moduleOf(JavaClass javaClass) {
        return moduleOf(javaClass.getPackageName());
    }

    static String moduleOf(String packageName) {
        if (!packageName.startsWith(BASE_PACKAGE + ".")) {
            return "";
        }
        String remainder = packageName.substring(BASE_PACKAGE.length() + 1);
        int dotIndex = remainder.indexOf('.');
        return dotIndex == -1 ? remainder : remainder.substring(0, dotIndex);
    }
}
