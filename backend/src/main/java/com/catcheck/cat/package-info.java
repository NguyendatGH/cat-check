/**
 * Hồ sơ mèo (giống, ngày sinh, đặc điểm sức khoẻ nền) thuộc sở hữu người dùng.
 *
 * <p>{@code "media::api"} chứ không phải bare {@code "media"}: Spring Modulith coi một entry trần
 * chỉ cấp quyền vào named-interface "unnamed" (type ở thẳng gói gốc module), KHÔNG lan sang
 * named-interface con dù có tên. {@code media.api.ImageStorage} và các type liên quan nằm ở
 * package con tên "api" (named interface), nên phải khai rõ {@code "media::api"} — xác nhận qua
 * chạy {@code ModularityTests} thật, cùng phát hiện với {@code identity::privacy::spi} (xem
 * {@code docs/handovers/A1-backend-fix.md}) và {@code scan::credit::api}
 * (xem {@code scan/package-info.java}).</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "identity", "credit", "media::api", "audit", "privacy::spi" })
package com.catcheck.cat;
