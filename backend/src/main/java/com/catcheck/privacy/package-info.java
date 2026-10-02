/**
 * Quyền riêng tư: yêu cầu xoá/xuất dữ liệu cá nhân. Định nghĩa named interface "spi"
 * (com.catcheck.privacy.spi) mà các module khác implement để tham gia cơ chế xoá dữ liệu xuyên
 * module (xem ArchUnit R10 — mọi class implement privacy.spi.ErasureParticipant phải nằm
 * trong package con "application.privacy" của module đó).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "identity", "notification", "audit" })
package com.catcheck.privacy;
