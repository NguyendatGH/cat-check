import { Navigate, Outlet, useLocation } from "react-router";
import { useSessionStore } from "@/entities/user";
import { ROUTE_PATTERNS } from "../routes";

export function RequireAuth() {
  const status = useSessionStore((state) => state.status);
  const location = useLocation();

  if (status !== "authenticated") {
    const next = encodeURIComponent(`${location.pathname}${location.search}`);
    return <Navigate to={`${ROUTE_PATTERNS.login}?next=${next}`} replace />;
  }

  return <Outlet />;
}
