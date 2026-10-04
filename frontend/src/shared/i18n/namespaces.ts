/**
 * 18 namespace i18n — khớp danh sách file locales/vi/*.json.
 * "shop" thêm cho module Cửa hàng/Giỏ hàng/Đơn hàng (Phase 3, hiện chỉ có UI + mock data).
 * "map" thêm cho module Bản đồ Chăm sóc Mèo / Chi tiết Phòng khám (Phase 2 theo p4 —
 * bảng `place`/`place_review` chưa đặc tả, hiện chỉ có UI + mock data).
 * "community" thêm cho module Cộng đồng (Phase 2 theo p4 — `post`/`comment` chưa đặc tả,
 * hiện chỉ có UI + mock data).
 */
export const NAMESPACES = [
  "common",
  "errors",
  "auth",
  "onboarding",
  "cat",
  "scan",
  "history",
  "trends",
  "reminder",
  "export",
  "credit",
  "notification",
  "settings",
  "admin",
  "legal",
  "shop",
  "map",
  "community",
  "assistant",
] as const;

export type Namespace = (typeof NAMESPACES)[number];
