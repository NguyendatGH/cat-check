/**
 * Bộ vai trò được phép vào từng khu vực và từng trang quản trị.
 *
 * Mỗi bộ khớp với một API ở backend (controller `Admin*`, `SecurityConfig`, các `*RoleGuard`).
 * Đổi ở đây thì phải đổi cùng backend, nếu không UI sẽ hiện trang mà API trả 403.
 *
 * Không dùng vai trò "ADMIN": backend không có vai trò này.
 */
export const ROLES = {
  /** Khu vực quản trị (AdminLayout): ai có một trong các vai trò này được vào /admin. */
  adminPortal: ["ADMIN_SUPER", "ADMIN_SUPPORT", "ADMIN_CATALOG", "DPO", "MODERATOR"],
  /** Khu vực người bán (SellerLayout): quản lý sản phẩm và danh mục. */
  seller: ["ADMIN_SUPER", "ADMIN_CATALOG"],

  dashboard: ["ADMIN_SUPER", "DPO", "ADMIN_SUPPORT", "ADMIN_CATALOG"],
  users: ["ADMIN_SUPER", "ADMIN_SUPPORT", "DPO"],
  activationCodes: ["ADMIN_SUPER", "ADMIN_SUPPORT"],
  activationCodesWrite: ["ADMIN_SUPER"],
  packagesRead: ["ADMIN_SUPER", "ADMIN_CATALOG", "ADMIN_SUPPORT"],
  packagesWrite: ["ADMIN_SUPER"],
  products: ["ADMIN_SUPER", "ADMIN_CATALOG"],
  colorChart: ["ADMIN_SUPER", "ADMIN_CATALOG"],
  careTipsWrite: ["ADMIN_SUPER", "ADMIN_CATALOG", "DPO"],
  careTipsPublish: ["ADMIN_SUPER", "DPO"],
  communityModeration: ["ADMIN_SUPER", "MODERATOR"],
  privacyRequests: ["ADMIN_SUPER", "ADMIN_SUPPORT", "DPO"],
  privacyErasureApproval: ["DPO"],
  retention: ["ADMIN_SUPER", "DPO"],
  retentionWrite: ["DPO"],
  broadcast: ["ADMIN_SUPER"],
  broadcastIncident: ["ADMIN_SUPER", "DPO"],
  jobs: ["ADMIN_SUPER", "DPO"],
  outbox: ["ADMIN_SUPER"],
  auditLog: ["ADMIN_SUPER", "ADMIN_SUPPORT", "DPO"],
  staff: ["ADMIN_SUPER"],
  scans: ["ADMIN_SUPER", "ADMIN_SUPPORT", "DPO"],
  scanImage: ["DPO"],
  scanReassign: ["ADMIN_SUPER", "ADMIN_SUPPORT"],
  monitoringRulesRead: ["ADMIN_SUPER", "ADMIN_CATALOG"],
  monitoringRulesWrite: ["ADMIN_SUPER"],
  securitySessionsPurge: ["ADMIN_SUPER"],
  securityIncidentsRead: ["ADMIN_SUPER", "DPO"],
  securityIncidentsWrite: ["DPO"],
  policyVersionsRead: ["ADMIN_SUPER", "DPO"],
  policyVersionsWrite: ["DPO"],
  consentsRead: ["DPO"],
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES][number];

/** Kiểm tra người dùng có một trong các vai trò được phép không. */
export function hasAnyRole(userRoles: readonly string[] | undefined, allowed: readonly string[]): boolean {
  return (userRoles ?? []).some((role) => allowed.includes(role));
}
