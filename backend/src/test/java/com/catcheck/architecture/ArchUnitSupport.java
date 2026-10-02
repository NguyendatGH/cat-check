package com.catcheck.architecture;

import com.tngtech.archunit.core.domain.JavaClass;

/** Hàm tiện ích dùng chung cho các rule test trong package này. */
final class ArchUnitSupport {

    private static final String BASE_PACKAGE = "com.catcheck";

    private ArchUnitSupport() {
    }

    /**
     * "com.catcheck.scan.domain.Foo" -> "scan". Trả về chuỗi rỗng nếu class không nằm dưới
     * {@code com.catcheck.<module>...} (ví dụ chính lớp {@code com.catcheck.CatCheckApplication}).
     */
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
