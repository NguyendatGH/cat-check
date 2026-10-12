import { defaultLandingPath, LANDING_PATHS } from "@/shared/auth/landing";

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
