/**
 * Model cho dải phân loại pH — khớp `PhBandResponse`
 * (`backend/src/main/java/com/catcheck/colorchart/api/dto/PhBandResponse.java`, bảng
 * `ph_classification_band`, p6 §6.7.1/§6.7.4). NGUỒN DUY NHẤT cho mọi hiển thị pH
 * (gauge, badge, chart, PDF) — KHÔNG hard-code ngưỡng hay nhãn ở FE (p9 §9.2.4, ESLint
 * `no-restricted-syntax` chặn identifier/literal pH).
 */

export type PhBandSeverity = "NORMAL" | "ATTENTION" | "WATCH" | "NEUTRAL";

/**
 * Chỉ 4 giá trị được phép (p6 §6.7.1, đã hoà giải R-F05) — component nghiệp vụ pH
 * không được gọi thẳng `color-success`/`color-warning`/`color-danger`.
 */
export type PhColorToken = "color-ph-normal" | "color-ph-mild" | "color-ph-abnormal" | "color-ph-unknown";

export interface PhBand {
  /** VD: `LOW`, `SLIGHTLY_LOW`, `IN_RANGE`, `SLIGHTLY_HIGH`, `HIGH`, `INCONCLUSIVE`. */
  code: string;
  /**
   * `null` HOẶC vắng mặt = -vô cực.
   *
   * Backend đặt `default-property-inclusion: non_null`, nên dải mở (`LOW`) KHÔNG có key này
   * trong JSON chứ không phải có key mang `null`. Khai `?` để TypeScript buộc mọi nơi đọc
   * phải xử lý `undefined` — bỏ sót chính là bug `findBandForPh` đã mắc.
   */
  phMin?: number | null;
  /** `null` HOẶC vắng mặt = +vô cực. Xem ghi chú ở {@link PhBand.phMin}. */
  phMax?: number | null;
  minInclusive: boolean;
  maxInclusive: boolean;
  severity: PhBandSeverity;
  /** Nhãn đã dịch theo `Accept-Language` — KHÔNG phải i18n key (server trả text). */
  label: string;
  description: string;
  colorToken: string;
  /** Tên icon Lucide dạng kebab-case do BE trả, VD `check-circle`, `alert-triangle`. */
  iconName: string;
  sortOrder: number;
  triggersAlert: boolean;
}

/** F5 — `GET /reference/color-charts/active`. KHÔNG chứa toạ độ Lab (tài sản hiệu chuẩn). */
export interface ActiveColorChart {
  code: string;
  version: number;
  placeholder: boolean;
  productLine: string | null;
}
