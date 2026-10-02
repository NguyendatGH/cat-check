/**
 * Named interface "spi" của module privacy: hợp đồng {@link com.catcheck.privacy.spi.ErasureParticipant}
 * mà các module có dữ liệu cá nhân (identity, cat, credit, scan...) implement để được module
 * privacy gọi lại khi xử lý yêu cầu xoá dữ liệu. Các module phụ thuộc vào "privacy::spi" trong
 * allowedDependencies của chính họ (xem bảng phụ thuộc ở mục 2 nhiệm vụ M0).
 */
@org.springframework.modulith.NamedInterface("spi")
package com.catcheck.privacy.spi;
