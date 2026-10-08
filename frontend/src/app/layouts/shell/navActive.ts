import { ROUTE_PATTERNS } from "../../router/routes";

/**
 * Nhóm "Lịch sử & Phân tích": `/history` (chỉ là redirect) và hai trang con của một bé mèo.
 * Hai trang con nằm dưới `/cats/:id/…` nên NavLink mặc định sẽ bật nhầm tab "Hồ sơ bé mèo"
 * (`/cats`, `end: false`) và để tab "Lịch sử" tắt sau khi redirect.
 */
export function isHistorySection(pathname: string): boolean {
  return pathname === ROUTE_PATTERNS.historyRedirect || /^\/cats\/[^/]+\/(history|trends)(\/|$)/.test(pathname);
}

/** `routerActive` = kết quả khớp tiền tố mặc định của NavLink; chỉ chỉnh lại hai tab bị chồng lấn. */
export function isNavItemActive(to: string, pathname: string, routerActive: boolean): boolean {
  if (to === ROUTE_PATTERNS.historyRedirect) return isHistorySection(pathname);
  if (to === ROUTE_PATTERNS.catsList) return routerActive && !isHistorySection(pathname);
  return routerActive;
}
