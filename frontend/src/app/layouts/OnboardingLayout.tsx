import { Outlet } from "react-router";

export function OnboardingLayout() {
  return (
    <div className="flex min-h-dvh flex-col bg-background px-4 py-6">
      <Outlet />
    </div>
  );
}
