/**
 * Cờ tính năng Phase 2/3 — TẤT CẢ tắt ở M0. Route vẫn đăng ký đủ (p9 §9.4.3), chỉ đổi
 * element sang ComingSoonPage khi cờ tắt (app/router/router.tsx).
 * TODO: nối remote config / admin toggle thật ở milestone sau.
 */
export const featureFlags = {
  community: false,
  map: false,
  shop: false,
} as const;

export type FeatureFlagKey = keyof typeof featureFlags;
