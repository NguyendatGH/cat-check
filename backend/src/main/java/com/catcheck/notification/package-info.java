/**
 * Nền tảng gửi thông báo (email, push FCM) dùng chung cho các module nghiệp vụ.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "media" })
package com.catcheck.notification;
