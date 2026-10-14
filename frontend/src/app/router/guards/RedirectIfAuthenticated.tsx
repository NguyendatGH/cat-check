import { Navigate, Outlet, useSearchParams } from "react-router";
import { useSessionStore } from "@/entities/user";
import { defaultLandingPath, resolveRedirectTarget } from "@/shared/auth/landing";

export function RedirectIfAuthenticated() {
  const status = useSessionStore((state) => state.status);
  const roles = useSessionStore((state) => state.user?.roles);
  const [searchParams] = useSearchParams();

  if (status === "idle" || status === "loading") {
    return null;
  }

  if (status === "authenticated") {
    const target = roles?.length ? resolveRedirectTarget(searchParams.get("next"), roles) : defaultLandingPath(roles);
    return <Navigate to={target} replace />;
  }

  return <Outlet />;
}
