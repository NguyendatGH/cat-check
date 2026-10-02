package com.catcheck.admin.application;

/**
 * Port (hướng application -> ngoài) mà tầng infrastructure implement để application không phải
 * phụ thuộc trực tiếp vào chi tiết kỹ thuật (JDBC...) — giữ đúng nguyên tắc "application không
 * phụ thuộc infrastructure của chính module mình" (ArchUnit R2). Spring sẽ inject implementation
 * cụ thể (nằm ở {@code admin.infrastructure}) lúc runtime qua constructor injection.
 */
public interface DatabaseHealthPort {

    boolean isDatabaseReachable();
}
