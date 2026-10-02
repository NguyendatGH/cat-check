import type { ReactNode } from "react";
import { hasRole, useSessionStore } from "@/entities/user";
import { ForbiddenPage } from "@/pages/system/ForbiddenPage";

export interface RequireRoleProps {
  roles: readonly string[];
  children: ReactNode;
}

/**
 * Guard bước 4 (p9 mục 6): đủ role? thiếu → render 403 TẠI CHỖ (không redirect) — khác UI
 * với "chưa đăng nhập" (RequireAuth redirect) và "thiếu entitlement" (RequireEntitlement
 * → UpsellPage). Dùng trực tiếp trên từng route admin vì mỗi route có role khác nhau
 * (xem bảng role trong p9 §9.4.3 mục D, đã áp trong router.tsx).
 */
export function RequireRole({ roles, children }: RequireRoleProps) {
  const user = useSessionStore((state) => state.user);

  if (!hasRole(user, roles)) {
    return <ForbiddenPage />;
  }

  return <>{children}</>;
}
