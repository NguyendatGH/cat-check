/**
 * Phân tích xu hướng sức khoẻ theo thời gian từ scan_analysis, phát cảnh báo (health_flag).
 *
 * <p>Chỉ phụ thuộc {@code scan::api} — mọi dữ liệu lịch sử scan cần cho rule R1-R4 (p6 §6.9) đọc
 * qua cổng {@code scan.api.ScanHistoryQuery} do module {@code scan} công bố. Tình trạng
 * {@code chart.is_placeholder} (nguyên tắc 5, p6 §6.9.1: R1-R3 tắt khi bảng màu placeholder)
 * không cần cổng riêng — đã có sẵn trong {@code scan.api.ScanSavedEvent.chartIsPlaceholder()}
 * của chính sự kiện kích hoạt đánh giá rule. insight không import {@code colorchart} trực tiếp
 * (đúng chiều phụ thuộc colorchart → scan đã chốt ở {@code colorchart/package-info.java}).
 * {@code cat}/{@code notification} bỏ khỏi danh sách vì insight không dùng (health_flag chỉ cần
 * {@code cat_id} dạng UUID thô, không cần đọc dữ liệu mèo; gửi thông báo push/in-app cho
 * {@code HealthFlagRaised} là M5, ngoài phạm vi MVP theo ORCHESTRATOR §2).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "scan::api" })
package com.catcheck.insight;
