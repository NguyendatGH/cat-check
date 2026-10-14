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

export function resolveRedirectTarget(next: string | null, roles?: readonly string[]): string {
  if (!roles?.length) return "/";
  const landing = defaultLandingPath(roles);

  if (!next || !next.startsWith("/") || next.startsWith("//") || next.includes("\\")) return landing;

  if (landing !== LANDING_PATHS.buyer) {
    const portal = landing === LANDING_PATHS.seller ? LANDING_PATHS.seller : LANDING_PATHS.admin;
    if (next !== portal && !next.startsWith(`${portal}/`)) return landing;
  }
  return next;
}
