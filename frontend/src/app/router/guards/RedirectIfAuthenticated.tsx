import { Navigate, Outlet } from "react-router";
import { useSessionStore } from "@/entities/user";
import { ROUTE_PATTERNS } from "../routes";

/**
 * Guard bước 2 (nhánh ngược, p9 mục 6): đã đăng nhập mà vào /auth/* hoặc "/" → redirect
 * /dashboard. Áp cho AuthLayout subtree và riêng route "/" (KHÔNG áp cho /legal/* — trang
 * pháp lý vẫn xem được dù đã đăng nhập).
 */
export function RedirectIfAuthenticated() {
  const status = useSessionStore((state) => state.status);

  if (status === "authenticated") {
    return <Navigate to={ROUTE_PATTERNS.dashboard} replace />;
  }

  return <Outlet />;
}
