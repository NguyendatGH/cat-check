/**
 * Nền tảng thông báo: hộp thư in-app, email outbox, web push FCM (p12).
 *
 * <p><b>Những gì module này sở hữu:</b> 5 bảng của {@code V13__notification.sql} —
 * {@code notification}, {@code notification_outbox}, {@code email_outbox},
 * {@code push_subscription} (p4 F2/F3/F5) — cộng với registry template của p12 §12.2, hai job
 * đẩy outbox của p12 §12.6.3, và nhóm endpoint G4–G11 của p8 §8.4.7. Bảng thứ sáu của V13,
 * {@code reminder}, thuộc module {@code reminder}.</p>
 *
 * <p><b>Một bảng đọc-nhưng-không-ghi:</b> {@code user_notification_preference} (p4 F4). Đường
 * GHI là p8 B11 {@code PUT /account/notification-preferences} thuộc {@code identity}; ở đây chỉ
 * có repository CHỈ ĐỌC để quyết định kênh. Hai module cùng ghi một bảng là hai nguồn sự thật.</p>
 *
 * <p><b>Chỉ phụ thuộc {@code shared}</b> (và {@code media} từ bản M0). Hai thứ cần từ module
 * khác — trạng thái consent ({@code consent_record}, của {@code privacy}) và thông tin người
 * nhận ({@code app_user}, của {@code identity}) — đọc thẳng bằng JDBC qua SPI hẹp ở
 * {@code application.spi}, KHÔNG import type Java của hai module đó. Với {@code privacy} thì
 * đây còn là bắt buộc: {@code privacy} đã khai phụ thuộc {@code notification}, nên chiều ngược
 * lại sẽ tạo chu trình. Cùng judgment call đã ghi ở {@code reminder/package-info.java}.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "media" })
package com.catcheck.notification;
