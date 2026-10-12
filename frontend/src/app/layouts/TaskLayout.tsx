import { Outlet, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { ArrowLeft } from "lucide-react";
import { DesktopAside } from "./shell/DesktopAside";
import { DesktopHeader } from "./shell/DesktopHeader";
import { OfflineBar } from "./shell/OfflineBar";
import appLogo from "@/shared/assets/images/web-auth/logo.png";

export function TaskLayout() {
  const { t } = useTranslation("common");
  const navigate = useNavigate();

  return (
    <div className="flex min-h-dvh bg-background">
      <DesktopAside />
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="header-glass sticky top-0 z-[var(--z-sticky-header)] border-b border-primary/10 px-3 lg:hidden">
          <div className="mx-auto flex h-14 max-w-[480px] items-center justify-between">
            <button
              type="button"
              onClick={() => {
                void navigate(-1);
              }}
              aria-label={t("actions.back")}
              className="flex size-11 items-center justify-center rounded-full text-text-primary transition-all duration-200 hover:bg-surface hover:text-primary-dark active:scale-95"
            >
              <ArrowLeft size={20} aria-hidden="true" />
            </button>
            <img src={appLogo} alt={t("app.name")} className="h-11 w-[122px] object-contain" />
            <span className="size-11" aria-hidden="true" />
          </div>
        </header>
        <DesktopHeader showBack />
        <OfflineBar className="top-14 lg:top-16" />
        <main className="w-full flex-1 px-4 py-6 lg:mx-auto lg:max-w-[992px] lg:px-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
