package com.catcheck.reminder.api.dto;

import java.util.List;

/**
 * I1 — danh sách lịch nhắc.
 *
 * <p>Không phân trang: bất biến I23 giới hạn mỗi mèo tối đa 3 lịch đang bật (3 {@code type}),
 * và {@code cat.max_cat_profiles} đã chặn số mèo — tổng luôn nhỏ. Thêm con trỏ phân trang ở
 * đây là phức tạp thừa.</p>
 */
public record ReminderListResponse(List<ReminderResponse> items) {
}
