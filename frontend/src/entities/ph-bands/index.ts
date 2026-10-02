// entities/ph-bands — dải phân loại pH đến TỪ API cấu hình (p9 §9.2.4, QĐ #5b, p6 §6.7.4).
// Mọi import từ bên ngoài PHẢI qua file này (boundaries/entry-point).
export type { PhBand, PhBandSeverity, PhColorToken, ActiveColorChart } from "./model";
export { usePhBands, useActiveColorChart, phBandsKeys, findBandForPh } from "./hooks";
export { phTokenStyle } from "./colorToken";
export type { PhTokenStyle } from "./colorToken";
export { PhBadge, PhGaugeBar, PhRangeLegend, PhDisclaimerNote } from "./components";
export type { PhBadgeProps, PhGaugeBarProps, PhRangeLegendProps } from "./components";
