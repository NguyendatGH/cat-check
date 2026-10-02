/**
 * Nhắc lịch theo dõi (quét cát định kỳ, nhắc credit sắp hết hạn) — p8 nhóm I, bảng
 * {@code reminder} ở V13.
 *
 * <p><b>Chỉ phụ thuộc {@code shared}.</b> Bản M0 khai thêm {@code cat}, {@code identity},
 * {@code notification} nhưng module này không import type Java nào của ba module đó: quyền sở
 * hữu mèo và entitlement đọc thẳng bằng JDBC qua SPI ({@code application/spi}) — cùng judgment
 * call đã ghi ở {@code export/package-info.java} và {@code scan}. Thu hẹp danh sách giữ cho đồ
 * thị phụ thuộc không có cạnh thừa; khai rộng hơn thực tế chỉ che mất vi phạm thật về sau.</p>
 *
 * <p>Phần gửi thông báo (push/email outbox, job) CHƯA hiện thực — V13 đã tạo đủ bảng nhưng
 * {@code notification} là một hệ thống con riêng (p12), làm sau. Lịch vì thế mới chỉ được tạo,
 * sửa, tắt và xuất .ics; chưa có gì tự bắn ra thông báo.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared" })
package com.catcheck.reminder;
