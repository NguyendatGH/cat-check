import { Outlet } from "react-router";

/** Layout toàn màn hình — không header/nav, dùng cho /scan (mobile), /offline, 404. */
export function FullscreenLayout() {
  return (
    <div className="min-h-dvh bg-background-alt">
      <Outlet />
    </div>
  );
}
