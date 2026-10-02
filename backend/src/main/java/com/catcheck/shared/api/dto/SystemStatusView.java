package com.catcheck.shared.api.dto;

import java.util.Map;

/**
 * K2 — trạng thái hệ thống công khai (p8 §8.4.11). Dùng cho banner bảo trì ở SPA.
 *
 * <p>KHÔNG chứa thông tin hạ tầng (kết nối DB, phiên bản thư viện...): đó là việc của
 * {@code /actuator/health}, vốn chỉ mở trong mạng nội bộ (p18). Endpoint này công khai nên
 * chỉ trả đúng thứ client cần để quyết định hiển thị gì.</p>
 *
 * @param buildVersion phiên bản app hiển thị cho user (badge "Phiên bản 2.4" — p4 dòng 1216
 *                     chốt giá trị này nằm ở {@code app_setting}, không phải dữ liệu user)
 * @param maintenance  cờ bảo trì; {@code active = false} khi chưa cấu hình
 * @param features     cờ tính năng CÔNG KHAI (namespace {@code feature.*}, đã loại khoá secret)
 */
public record SystemStatusView(
        String buildVersion,
        MaintenanceView maintenance,
        Map<String, String> features
) {

    /**
     * @param active true = đang bảo trì, SPA hiện banner chặn
     * @param until  mốc dự kiến xong (ISO-8601 dạng chuỗi như đã cấu hình); null nếu không đặt
     */
    public record MaintenanceView(boolean active, String until) {
    }
}
