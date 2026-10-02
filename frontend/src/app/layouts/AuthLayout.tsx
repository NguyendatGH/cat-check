import { Outlet, useMatches } from "react-router";
import { useTranslation } from "react-i18next";
import { TopBar } from "@/shared/ui";
import webLogo from "@/shared/assets/images/web-auth/logo.png";
import headerShield from "@/shared/assets/icons/web-auth/header-shield.svg";

interface AuthRouteHandle {
  titleKey?: string;
}

/**
 * Layout cho /auth/*.
 *
 * Figma có HAI bộ frame cho cùng các màn này:
 * - mobile 390px (01b, 01c-1, 01c-2) → `TopBar` task-level + cột hẹp `max-w-sm`
 * - web 1280px (`Web - 01…` 16:7569, 16:6784, 16:8427) → header cao 78px (logo + slogan
 *   giám sát) + container `max-w-[1280px]` padding 48px, các trang tự dựng lưới 12 cột.
 *
 * Một trang React phục vụ cả hai (p9): chrome mobile ẩn từ `lg` trở lên và ngược lại, KHÔNG
 * render hai lần nội dung — phần thân do từng page tự xử lý responsive.
 */
export function AuthLayout() {
  const { t } = useTranslation("auth");
  const matches = useMatches();
  const titleKey = matches.map((m) => (m.handle as AuthRouteHandle | undefined)?.titleKey).find(Boolean);

  return (
    <div className="flex min-h-dvh flex-col bg-background">
      {/* Chrome mobile (Figma 390px) */}
      <div className="lg:hidden">
        <TopBar variant="task-level" title={titleKey ? t(titleKey) : ""} />
      </div>

      {/* Header web (Figma 16:9599 / 16:8635) — cao 78px, nền mờ + blur */}
      <header className="sticky top-0 z-[var(--z-sticky-header)] hidden w-full bg-[rgba(250,248,255,0.8)] shadow-[0px_1px_8px_0px_rgba(0,0,0,0.04)] backdrop-blur-[12px] lg:block">
        <div className="mx-auto flex h-[78px] max-w-[1280px] items-center justify-between px-12">
          <img src={webLogo} alt={t("pages.login.title")} className="h-20 w-[120px] object-contain" />
          <div className="flex items-center gap-1">
            <img src={headerShield} alt="" className="h-[15px] w-3" />
            <span className="text-[12px] font-bold tracking-[0.3px] text-text-secondary">
              {t("web.headerTagline")}
            </span>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-sm flex-1 px-4 py-6 lg:max-w-[1280px] lg:px-12 lg:py-12">
        <Outlet />
      </main>
    </div>
  );
}
