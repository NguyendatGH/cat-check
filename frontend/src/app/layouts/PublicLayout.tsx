import { Outlet, Link, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { ArrowLeft } from "lucide-react";
import { useHomePath, useSessionStore } from "@/entities/user";

export function PublicLayout() {
  const { t } = useTranslation("common");
  const navigate = useNavigate();
  const signedIn = useSessionStore((state) => state.status === "authenticated");
  const roleHome = useHomePath();
  const home = signedIn ? roleHome : "/";

  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <header className="sticky top-0 z-[var(--z-sticky-header)] flex items-center gap-1 border-b border-border bg-surface px-2 py-2">
        <button
          type="button"
          aria-label={t("actions.back")}
          className="flex size-10 shrink-0 items-center justify-center rounded-full text-text-primary transition-colors hover:bg-background-alt active:bg-chip-bg"
          onClick={() => {
            // react-router stores the in-app history index; 0 means this page was opened directly.
            const idx = (window.history.state as { idx?: number } | null)?.idx ?? 0;
            void (idx > 0 ? navigate(-1) : navigate(home));
          }}
        >
          <ArrowLeft size={20} aria-hidden="true" />
        </button>
        <Link to={home} className="text-h3 font-bold text-primary">
          {t("app.name")}
        </Link>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}
