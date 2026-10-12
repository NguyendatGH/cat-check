import { Link, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { ArrowLeft } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { useSessionStore } from "@/entities/user";
import { phTokenStyle, usePhBands } from "@/entities/ph-bands";
import { useScanHistory } from "@/features/history";
import { NotificationUnreadBadge } from "@/features/notification";
import iconBell from "@/shared/assets/icons/web-dashboard/header-bell.svg";
import iconAvatar from "@/shared/assets/icons/web-dashboard/header-avatar.svg";
import { ROUTE_PATTERNS } from "../../router/routes";
import { HeaderSearch } from "./HeaderSearch";

function LatestScanPill({ enabled }: { enabled: boolean }) {
  const { t } = useTranslation("common");
  const { data } = useScanHistory(undefined, "ALL", enabled);
  const { data: bands } = usePhBands();
  const latest = data?.pages[0]?.items[0];
  const band = latest ? bands?.find((b) => b.code === latest.bandCode) : undefined;
  if (!latest || !band) return null;
  const style = phTokenStyle(band.colorToken);
  const value =
    latest.phValue != null
      ? t("webShell.latestScanValue", { value: latest.phValue.toFixed(1), label: band.label })
      : band.label;
  return (
    <Link
      to={`/scans/${latest.scanId}`}
      className={cn("hidden h-10 max-w-[280px] items-center gap-2 rounded-full pl-3 pr-4 xl:flex", style.bg)}
    >
      <span aria-hidden="true" className={cn("size-2.5 shrink-0 rounded-full", style.solid)} />
      <span className="flex min-w-0 flex-col">
        <span className="truncate text-[10px] leading-[13px] text-text-secondary">
          {latest.catName
            ? t("webShell.latestScanLabel", { name: latest.catName })
            : t("webShell.latestScanLabelNoCat")}
        </span>
        <span className={cn("truncate text-[11px] font-semibold leading-[14px]", style.text)}>{value}</span>
      </span>
    </Link>
  );
}

export function DesktopHeader({ showBack = false }: { showBack?: boolean }) {
  const { t } = useTranslation("common");
  const navigate = useNavigate();
  const user = useSessionStore((s) => s.user);

  return (
    <header className="header-glass sticky top-0 z-[var(--z-sticky-header)] hidden h-16 w-full shrink-0 items-center gap-4 border-b border-border/60 px-6 lg:flex">
      {showBack ? (
        <button
          type="button"
          onClick={() => {
            void navigate(-1);
          }}
          aria-label={t("actions.back")}
          className="flex size-10 shrink-0 items-center justify-center rounded-xl text-text-secondary transition-all duration-200 hover:bg-background-alt active:scale-95"
        >
          <ArrowLeft size={20} aria-hidden="true" />
        </button>
      ) : null}

      <HeaderSearch signedIn={Boolean(user)} />

      <div className="ml-auto flex shrink-0 items-center gap-4">
        <LatestScanPill enabled={Boolean(user)} />

        <button
          type="button"
          onClick={() => {
            void navigate(ROUTE_PATTERNS.notifications);
          }}
          aria-label={t("nav.notifications")}
          className="relative flex size-10 items-center justify-center rounded-xl transition-all duration-200 hover:bg-background-alt active:scale-95"
        >
          <img src={iconBell} alt="" className="h-[16.667px] w-[13.333px]" />
          <NotificationUnreadBadge enabled={Boolean(user)} />
        </button>

        {user ? (
          <button
            type="button"
            onClick={() => {
              void navigate(ROUTE_PATTERNS.settingsProfile);
            }}
            className="flex max-w-[260px] items-center gap-3 rounded-xl py-1 pl-2 transition-all duration-200 hover:bg-background-alt active:scale-[0.98]"
          >
            <span className="min-w-0 text-right">
              <span className="block truncate text-caption font-bold text-text-primary">{user.displayName}</span>
              <span className="block truncate text-[11px] leading-[14px] text-text-secondary">{user.email}</span>
            </span>
            <span className="flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-full bg-primary-dark">
              {user.avatarUrl ? (
                <img src={user.avatarUrl} alt="" className="size-full object-cover" />
              ) : (
                <img src={iconAvatar} alt="" className="size-3" />
              )}
            </span>
          </button>
        ) : (
          <button
            type="button"
            onClick={() => {
              void navigate(ROUTE_PATTERNS.login);
            }}
            className="flex h-9 items-center justify-center rounded-xl bg-primary px-4 text-caption font-semibold text-white transition-all duration-200 hover:bg-primary-dark active:scale-95"
          >
            {t("dashboard.guestBanner.login")}
          </button>
        )}
      </div>
    </header>
  );
}

