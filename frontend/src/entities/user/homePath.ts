import { defaultLandingPath } from "@/shared/auth/landing";
import { useSessionStore } from "./store";

/**
 * Trang "về trang chủ" theo vai trò: buyer về /dashboard, tài khoản staff về cổng admin/seller
 * (/dashboard bị RequireNonAdminRole chặn với staff nên dẫn thẳng tới đó sẽ ra trang 403).
 */
export function useHomePath(): string {
  const roles = useSessionStore((state) => state.user?.roles);
  return defaultLandingPath(roles);
}
