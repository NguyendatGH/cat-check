/**
 * Types của module Cảnh báo (`health_flag`) — chép NGUYÊN VĂN response của `InsightController`
 * (nhóm G1–G3 + F4 của p8 §8.4.7/§8.4.6), đọc thẳng từ
 * `backend/.../insight/api/dto/HealthFlagResponse.java`.
 *
 * Không thêm field nào backend chưa trả: thiếu dữ liệu thì UI bỏ phần đó, không bịa.
 */

/** `monitoring_rule.severity` (V12__monitoring.sql). */
export type HealthFlagSeverity = "INFO" | "ATTENTION" | "URGENT";

/** G1/G2 — một dấu hiệu theo dõi. */
export interface HealthFlagView {
  flagId: string;
  catId: string;
  /** Mã rule đã kích hoạt (`monitoring_rule.code`), VD `REPEATED_OUT_OF_RANGE`. */
  ruleCode: string;
  severity: HealthFlagSeverity;
  /** `null` khi flag không gắn với một lần quét cụ thể. */
  triggerScanId: string | null;
  triggeredAt: string;
  windowFrom: string;
  windowTo: string;
  /** Khoá i18n của rule (quyết định #15) — chỉ dùng làm khoá tra, không in thẳng ra UI. */
  messageKey: string;
  messageParams: Record<string, unknown>;
  /**
   * Câu chữ tiếng Việt server đã render và lưu lại tại thời điểm bắn cảnh báo
   * (`health_flag.explanation_vi`). Đây là nội dung HIỂN THỊ — giữ nguyên để trùng khớp với
   * bản đã gửi qua push/PDF, không render lại ở client.
   */
  explanationVi: string | null;
  acknowledgedAt: string | null;
  acknowledged: boolean;
  /** Khoá i18n của câu miễn trừ y tế bắt buộc kèm theo (p15 §15.7.2). */
  disclaimerKey: string;
}

/** G1 — `GET /health-flags`, phân trang con trỏ. */
export interface HealthFlagListResponse {
  items: HealthFlagView[];
  hasMore: boolean;
  nextCursor: string | null;
}

/** Bộ lọc của G1. */
export interface HealthFlagFilter {
  catId?: string | undefined;
  acknowledged?: boolean | undefined;
  severity?: HealthFlagSeverity | undefined;
  limit?: number | undefined;
}
