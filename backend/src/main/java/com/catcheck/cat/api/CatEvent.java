package com.catcheck.cat.api;

/**
 * Khoá cho các sự kiện miền mà module cat phát ra.
 *
 * <p>Đặt ở {@code cat.api} vì người tiêu thụ (notification, insight, audit) đều là module KHÁC, và
 * p7 §7.3 cấm để kiểu domain đi qua biên module. Đây cũng là lý do {@code cat.domain} không được
 * khai báo sự kiện: khai ở đó thì mọi module muốn nghe đều phải phụ thuộc domain của cat.</p>
 *
 * <p>Module cat KHÔNG tự ghi {@code outbox_event}. Việc đó thuộc W3 (bảng ở V5) vì nó là hạ tầng
 * dùng chung; xem {@code docs/handovers/A3.md}.</p>
 */
public interface CatEvent {

    /**
     * Định danh loại sự kiện, ghi vào cột {@code outbox_event.aggregate_type}.
     *
     * <p>Hằng số, không phải {@code getClass().getSimpleName()}: đổi tên class không được làm hỏng
     * dữ liệu outbox đã ghi.</p>
     */
    String eventType();
}
