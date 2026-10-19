import { useTranslation } from "react-i18next";
import { WifiOff } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { useOnlineStatus } from "@/shared/lib/hooks/useOnlineStatus";

export function OfflineBar({ className }: { className?: string }) {
  const { t } = useTranslation("errors");
  const online = useOnlineStatus();
  if (online) return null;
  return (
    <div
      role="status"
      className={cn(
        "sticky z-[var(--z-dropdown)] flex h-8 w-full shrink-0 items-center justify-center gap-2 bg-warning-bg px-4 text-[12px] leading-4 text-warning-text",
        className,
      )}
    >
      <WifiOff size={14} className="shrink-0" aria-hidden="true" />
      <span className="truncate lg:hidden">{t("network.offline")}</span>
      <span className="hidden truncate lg:inline">{t("offlinePage.statusOffline")}</span>
    </div>
  );
}
