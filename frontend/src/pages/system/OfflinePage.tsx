import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { RefreshCw, Wifi, WifiOff } from "lucide-react";
import { Button } from "@/shared/ui";
import { useOnlineStatus } from "@/shared/lib/hooks/useOnlineStatus";
import { useHomePath } from "@/entities/user";

export function OfflinePage() {
  const { t } = useTranslation(["errors", "common"]);
  const homePath = useHomePath();
  const online = useOnlineStatus();

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col justify-center gap-6 px-4 py-10">
      <header className="flex flex-col items-center gap-3 text-center">
        <span
          className="flex size-14 items-center justify-center rounded-2xl bg-chip-bg text-primary-dark"
          aria-hidden="true"
        >
          <WifiOff size={26} />
        </span>
        <h1 className="text-h2 font-bold text-text-primary">{t("common:pages.offline.title")}</h1>
        <p className="text-body text-text-secondary">{t("errors:offlinePage.lead")}</p>
      </header>

      <div className="flex flex-col gap-3 rounded-2xl bg-surface p-5 shadow-brand-md">
        <p className="text-caption leading-relaxed text-text-secondary">{t("errors:offlinePage.body")}</p>
        <p
          aria-live="polite"
          className={
            online
              ? "flex items-center gap-2 rounded-xl bg-success-bg px-3 py-2 text-caption font-semibold text-success-text"
              : "flex items-center gap-2 rounded-xl bg-background-alt px-3 py-2 text-caption font-semibold text-text-secondary"
          }
        >
          {online ? (
            <Wifi size={15} className="shrink-0" aria-hidden="true" />
          ) : (
            <WifiOff size={15} className="shrink-0" aria-hidden="true" />
          )}
          {online ? t("errors:offlinePage.statusOnline") : t("errors:offlinePage.statusOffline")}
        </p>
      </div>

      <div className="flex flex-col gap-2">
        <Button
          type="button"
          size="lg"
          leftIcon={<RefreshCw size={18} aria-hidden="true" />}
          onClick={() => {
            window.location.reload();
          }}
        >
          {t("errors:offlinePage.retryCta")}
        </Button>
        <Link
          to={homePath}
          className="flex min-h-11 items-center justify-center rounded-xl border border-border px-4 text-body font-semibold text-primary hover:bg-background-alt"
        >
          {t("errors:offlinePage.homeCta")}
        </Link>
      </div>

      <section className="flex flex-col gap-2 rounded-2xl bg-surface p-5 shadow-brand-md">
        <h2 className="text-caption font-bold text-text-primary">{t("errors:offlinePage.tipsTitle")}</h2>
        <ul className="flex list-disc flex-col gap-1.5 pl-5 text-caption leading-relaxed text-text-secondary">
          <li>{t("errors:offlinePage.tip1")}</li>
          <li>{t("errors:offlinePage.tip2")}</li>
          <li>{t("errors:offlinePage.tip3")}</li>
        </ul>
      </section>
    </div>
  );
}
