import { Outlet } from "react-router";
import { ProductTour } from "@/features/product-tour";
import { DesktopAside } from "./shell/DesktopAside";
import { DesktopHeader } from "./shell/DesktopHeader";
import { BottomNav, MobileAppHeader } from "./shell/MobileChrome";
import { OfflineBar } from "./shell/OfflineBar";

export function AppLayout() {
  return (
    <div className="flex min-h-dvh bg-background">
      <DesktopAside />
      <div className="flex min-w-0 flex-1 flex-col">
        <MobileAppHeader />
        <DesktopHeader />
        <OfflineBar className="top-16" />
        <main className="flex-1 pb-[calc(72px+var(--space-4)+env(safe-area-inset-bottom))] lg:w-full lg:px-8 lg:py-6 lg:pb-8 xl:px-10 2xl:px-12">
          <Outlet />
        </main>
      </div>
      <BottomNav />
      <ProductTour />
    </div>
  );
}
