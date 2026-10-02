import { Navigate, Outlet, useLocation } from "react-router";
import { useSessionStore } from "@/entities/user";
import { ROUTE_PATTERNS } from "../routes";

/**
 * Guard bước 2 (p9 mục 6): chưa đăng nhập → redirect /auth/login?next=<path> (replace).
 * SessionProvider đã chặn render router tới khi session bootstrap xong (guard bước 1),
 * nên ở đây `status` luôn là "authenticated" | "anonymous".
 */
export function RequireAuth() {
  const status = useSessionStore((state) => state.status);
  const location = useLocation();

  if (status !== "authenticated") {
    const next = encodeURIComponent(`${location.pathname}${location.search}`);
    return <Navigate to={`${ROUTE_PATTERNS.login}?next=${next}`} replace />;
  }

  return <Outlet />;
}
