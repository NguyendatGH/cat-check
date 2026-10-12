import { Outlet } from "react-router";

export function FullscreenLayout() {
  return (
    <div className="min-h-dvh bg-background-alt">
      <Outlet />
    </div>
  );
}
