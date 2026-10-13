import type { ReactNode } from "react";
import { useSessionStore, hasNonAdminRole } from "@/entities/user";
import { ForbiddenPage } from "@/pages/system/ForbiddenPage";

export function RequireNonAdminRole({ children }: { children: ReactNode }) {
  const user = useSessionStore((state) => state.user);
  if (!hasNonAdminRole(user)) return <ForbiddenPage />;
  return <>{children}</>;
}
