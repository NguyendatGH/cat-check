import { Outlet, Link } from "react-router";
import { useTranslation } from "react-i18next";

/** Layout công khai — trang chủ, trang pháp lý. Header tối giản, không cần session. */
export function PublicLayout() {
  const { t } = useTranslation("common");
  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <header className="sticky top-0 z-[var(--z-sticky-header)] border-b border-border bg-surface px-4 py-3">
        <Link to="/" className="text-h3 font-bold text-primary">
          {t("app.name")}
        </Link>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}
