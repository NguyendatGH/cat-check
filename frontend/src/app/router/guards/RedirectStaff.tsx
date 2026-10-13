import type { ReactNode } from "react";
import { Navigate, Outlet } from "react-router";
import { useSessionStore } from "@/entities/user";
import { defaultLandingPath } from "@/shared/auth/landing";
import { hasAnyRole, ROLES } from "@/shared/auth/roles";

// Tài khoản staff không dùng giao diện buyer: đẩy về cổng admin/seller tương ứng.
export function RedirectStaff({ children }: { children?: ReactNode }) {
  const roles = useSessionStore((state) => state.user?.roles);

  if (hasAnyRole(roles, ROLES.adminPortal)) {
    return <Navigate to={defaultLandingPath(roles)} replace />;
  }

  return children ?? <Outlet />;
}
