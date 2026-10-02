import { Outlet } from "react-router";

/** Layout luồng onboarding — full-bleed, không nav chính (đang trong wizard nhiều bước). */
export function OnboardingLayout() {
  return (
    <div className="flex min-h-dvh flex-col bg-background px-4 py-6">
      <Outlet />
    </div>
  );
}
