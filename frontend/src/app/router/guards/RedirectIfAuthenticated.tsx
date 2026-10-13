import { Navigate, Outlet } from "react-router";
import { useSessionStore } from "@/entities/user";
import { defaultLandingPath } from "@/shared/auth/landing";

export function RedirectIfAuthenticated() {
  const status = useSessionStore((state) => state.status);
  const roles = useSessionStore((state) => state.user?.roles);

  // Wait for session to load
  if (status === "loading") {
    return null;
  }

  // Redirect authenticated users away from auth pages
  if (status === "authenticated") {
    return <Navigate to={defaultLandingPath(roles)} replace />;
  }

  return <Outlet />;
}
