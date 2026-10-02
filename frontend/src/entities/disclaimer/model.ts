/**
 * Model tối thiểu cho trạng thái chấp nhận medical disclaimer.
 * Nội dung disclaimer thật lấy từ spec/reference/screens/ — KHÔNG bịa copy y tế ở đây.
 */
export interface DisclaimerAcceptance {
  policyCode: string;
  version: string;
  acceptedAt: string | null;
}

/**
 * 5 biến thể `DisclaimerBanner` (p10 §5.2), ánh xạ 1-1 với ma trận vị trí hiển thị
 * p15 §15.7.2: `D-SHORT`/`D-MED`/`D-LONG`/`EMERGENCY`/`FOOTER-LINE`. Câu chữ nằm trong
 * i18n namespace `legal` (bản nháp p15 §15.7.3) — component không hard-code text.
 */
export type DisclaimerVariant = "short" | "medium" | "long" | "emergency" | "footerLine";
