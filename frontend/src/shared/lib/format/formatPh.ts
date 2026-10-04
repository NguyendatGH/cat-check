/**
 * Format giá trị pH để hiển thị. KHÔNG chứa ngưỡng phân loại (band) — ngưỡng luôn đến
 * từ API (entities/ph-bands), file này chỉ format con số thô.
 */
export function formatPh(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "—";
  }
  return new Intl.NumberFormat("vi-VN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}
