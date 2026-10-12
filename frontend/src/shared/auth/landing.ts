import { hasAnyRole, ROLES } from "./roles";

export const LANDING_PATHS = {
  buyer: "/dashboard",
  admin: "/admin",
  seller: "/seller",
  moderator: "/admin/community/reports",
} as const;

export function defaultLandingPath(roles: readonly string[] | undefined): string {
  if (hasAnyRole(roles, ["ADMIN_SUPER", "ADMIN_SUPPORT", "DPO"])) return LANDING_PATHS.admin;
  if (hasAnyRole(roles, ["ADMIN_CATALOG"])) return LANDING_PATHS.seller;
  if (hasAnyRole(roles, ROLES.communityModeration)) return LANDING_PATHS.moderator;
  return LANDING_PATHS.buyer;
}
