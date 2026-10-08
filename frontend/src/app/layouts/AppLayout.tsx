import { Outlet } from "react-router";
import { DesktopAside } from "./shell/DesktopAside";
import { DesktopHeader } from "./shell/DesktopHeader";
import { BottomNav, MobileAppHeader } from "./shell/MobileChrome";
import { OfflineBar } from "./shell/OfflineBar";

/**
 * Layout ứng dụng chính.
 *
 * - `< lg`: chrome mobile của Figma 390px — header dính trên + bottom nav có FAB "Quét".
 * - `>= lg`: chrome desktop của Figma `Web - 02` — Aside 288px (cột căng hết chiều cao trang,
 *   menu bên trong sticky) + Header 64px, vùng nội dung mở rộng theo phần viewport còn lại với
 *   padding responsive. Trang con KHÔNG tự đặt padding ngang ở desktop.
 * - Mất mạng: thanh `OfflineBar` dính ngay dưới header (frame `Sys - 07`).
 */
export function AppLayout() {
  return (
    <div className="flex min-h-dvh bg-background">
      <DesktopAside />
      <div className="flex min-w-0 flex-1 flex-col">
        <MobileAppHeader />
        <DesktopHeader />
        <OfflineBar className="top-14 lg:top-16" />
        <main className="flex-1 pb-[calc(64px+var(--space-4)+env(safe-area-inset-bottom))] lg:w-full lg:px-8 lg:py-6 lg:pb-8 xl:px-10 2xl:px-12">
          <Outlet />
        </main>
      </div>
      <BottomNav />
    </div>
  );
}
