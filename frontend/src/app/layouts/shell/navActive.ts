import { ROUTE_PATTERNS } from "../../router/routes";

export function isHistorySection(pathname: string): boolean {
  return pathname === ROUTE_PATTERNS.historyRedirect || /^\/cats\/[^/]+\/(history|trends)(\/|$)/.test(pathname);
}

export function isNavItemActive(to: string, pathname: string, routerActive: boolean): boolean {
  if (to === ROUTE_PATTERNS.historyRedirect) return isHistorySection(pathname);
  if (to === ROUTE_PATTERNS.catsList) return routerActive && !isHistorySection(pathname);
  return routerActive;
}
