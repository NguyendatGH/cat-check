/**
 * Quản trị hệ thống.
 *
 * <p>TODO(M1+): đủ module nghiệp vụ ổn định thì bổ sung api read-only của các module cần cho
 * màn hình quản trị (ví dụ {@code identity::api}, {@code cat::api}, {@code scan::api}...) vào
 * {@code allowedDependencies}. Ở M0 để tối thiểu ({@code shared}, {@code audit}) đúng theo yêu
 * cầu nhiệm vụ M0 ("để trống danh sách cụ thể ở M0, ghi TODO") — chưa khai các module khác vì
 * admin chưa có class nghiệp vụ nào thật sự đọc dữ liệu của chúng.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "audit" })
package com.catcheck.admin;
