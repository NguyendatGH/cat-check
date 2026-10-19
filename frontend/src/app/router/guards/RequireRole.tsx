import type { ReactNode } from "react";
import { hasRole, useSessionStore } from "@/entities/user";
import { ForbiddenPage } from "@/pages/system/ForbiddenPage";

export interface RequireRoleProps {
  roles: readonly string[];
  children: ReactNode;
}

export function RequireRole({ roles, children }: RequireRoleProps) {
  const user = useSessionStore((state) => state.user);

  if (!hasRole(user, roles)) {
    return <ForbiddenPage />;
  }

  return <>{children}</>;
}
