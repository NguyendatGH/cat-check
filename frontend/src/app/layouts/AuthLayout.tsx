import { Link, Outlet, useMatches } from "react-router";
import { useTranslation } from "react-i18next";
import { TopBar } from "@/shared/ui";
import webLogo from "@/shared/assets/images/web-auth/logo.png";
import headerShield from "@/shared/assets/icons/web-auth/header-shield.svg";

interface AuthRouteHandle {
  titleKey?: string;
  hideTopBar?: boolean;
}

export function AuthLayout() {
  const { t } = useTranslation("auth");
  const matches = useMatches();
  const titleKey = matches.map((m) => (m.handle as AuthRouteHandle | undefined)?.titleKey).find(Boolean);
  const hideTopBar = matches.some((m) => (m.handle as AuthRouteHandle | undefined)?.hideTopBar);

  return (
    <div className="flex min-h-dvh flex-col bg-background">
      {!hideTopBar ? (
        <div className="lg:hidden">
          <TopBar variant="task-level" title={titleKey ? t(titleKey) : ""} />
        </div>
      ) : null}

      <header className="header-glass sticky top-0 z-[var(--z-sticky-header)] hidden w-full border-b border-border/40 lg:block">
        <div className="mx-auto flex h-[78px] max-w-[1280px] items-center justify-between px-12">
          <Link to="/" className="inline-flex items-center" aria-label={t("pages.login.title")}>
            <img src={webLogo} alt={t("pages.login.title")} className="h-20 w-[120px] object-contain" />
          </Link>
          <div className="flex items-center gap-1">
            <img src={headerShield} alt="" className="h-[15px] w-3" />
            <span className="text-[12px] font-bold tracking-[0.3px] text-text-secondary">{t("web.headerTagline")}</span>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-sm flex-1 px-4 py-6 lg:max-w-[1280px] lg:px-12 lg:py-12">
        <Outlet />
      </main>
    </div>
  );
}
