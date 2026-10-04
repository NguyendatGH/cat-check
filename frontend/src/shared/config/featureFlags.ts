/**
 * Cờ tính năng Phase 2/3. Community, map/place và shop đã có migration + API backend nên
 * được mở trong bản local này; scan và AI chat không thuộc phạm vi thay đổi.
 *
 * Kiểu là `Record<FeatureFlagKey, boolean>` chứ KHÔNG phải `as const`: với `as const` thì
 * mọi giá trị là literal `false`, TypeScript rút gọn `featureFlags.shop ? a : b` thành `b`
 * và rule `no-unnecessary-condition` báo lỗi ngay tại chỗ kiểm cờ — tức là cờ chỉ "bật được"
 * nếu sửa cả file này lẫn chỗ dùng. `boolean` giữ cho việc bật cờ là đổi đúng một giá trị.
 */
export type FeatureFlagKey = "community" | "map" | "shop";

export const featureFlags: Record<FeatureFlagKey, boolean> = {
  community: true,
  map: true,
  shop: true,
};
