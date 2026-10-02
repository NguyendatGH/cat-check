import { Navigate, Outlet } from "react-router";
import { useSessionStore } from "@/entities/user";
import { ROUTE_PATTERNS } from "../routes";

const ONBOARDING_STEP_PATH: Record<string, string> = {
  cat: ROUTE_PATTERNS.onboardingCat,
  "health-survey": ROUTE_PATTERNS.onboardingHealthSurvey,
  disclaimer: ROUTE_PATTERNS.onboardingDisclaimer,
  activate: ROUTE_PATTERNS.onboardingActivate,
};

/**
 * Guard bước 3 (p9 mục 6): đã đăng nhập nhưng CHƯA xong onboarding → redirect đúng bước
 * còn dở (KHÔNG luôn về bước 1). Đặt SAU RequireAuth trong cây route.
 */
export function RequireOnboarding() {
  const user = useSessionStore((state) => state.user);

  if (user && !user.onboarding.completed) {
    const target = user.onboarding.nextStep ? ONBOARDING_STEP_PATH[user.onboarding.nextStep] : undefined;
    return <Navigate to={target ?? ROUTE_PATTERNS.onboardingCat} replace />;
  }

  return <Outlet />;
}
