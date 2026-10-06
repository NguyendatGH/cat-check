package com.catcheck.insight.api.dto;

import java.util.List;

/**
 * L38 — bọc {@code items} như mọi danh sách khác (p8 §8.1.4). Không phân trang: bảng
 * {@code monitoring_rule} có đúng 5 dòng seed (p4 D11) và được nạp toàn bộ vào cache ứng dụng,
 * nên thêm cursor ở đây chỉ là nghi thức.
 */
public record AdminMonitoringRuleListResponse(List<AdminMonitoringRuleResponse> items) {
}
