/**
 * Nhắc lịch theo dõi (quét cát định kỳ, nhắc credit sắp hết hạn) — p8 nhóm I, bảng
 * {@code reminder} ở V13.
 *
 * <p><b>Phụ thuộc {@code shared} + {@code notification::api}.</b> Bản M0 khai thêm {@code cat}
 * và {@code identity} nhưng module này không import type Java nào của hai module đó: quyền sở
 * hữu mèo và entitlement đọc thẳng bằng JDBC qua SPI ({@code application/spi}) — cùng judgment
 * call đã ghi ở {@code export/package-info.java} và {@code scan}. Thu hẹp danh sách giữ cho đồ
 * thị phụ thuộc không có cạnh thừa; khai rộng hơn thực tế chỉ che mất vi phạm thật về sau.</p>
 *
 * <p>Cạnh {@code notification::api} có từ W2-B: {@code SendDueRemindersJob} (p12 §12.6.3) gọi
 * {@code NotificationGateway.enqueue} để xếp thông báo vào outbox. Đây là cạnh <b>bắt buộc</b>
 * chứ không phải tiện tay — p12 §12.6.1 quy tắc 8 cấm job tự gửi push/email trong thread của
 * mình, nên phải đi qua đúng cổng đó. Chiều ngược lại ({@code notification} → {@code reminder})
 * không tồn tại, nên không sinh chu trình.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "notification::api" })
package com.catcheck.reminder;
