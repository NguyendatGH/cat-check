import { useTranslation } from "react-i18next";
import { WifiOff } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { useOnlineStatus } from "@/shared/lib/hooks/useOnlineStatus";

/**
 * Thanh chỉ báo mất mạng toàn cục — frame `Sys - 07` / `Web - Sys - 07`
 * (`design/figma-plugin/src/40-system.js` `x2_offlineBar`): bar 32px nền `warning-bg`, icon
 * `wifi-off` + một câu, dính NGAY DƯỚI header và chỉ chạy hết bề ngang vùng nội dung (không đè
 * sidebar 288px). Không có mạng thì không hiện gì cả — không chiếm chỗ.
 *
 * `className` nhận offset `top-*` khớp chiều cao header của layout đang dùng (AppLayout mobile
 * 56px, TaskLayout mobile 68px, desktop 64px).
 */
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
      {/* Câu ngắn ở mobile (Figma `Sys - 07` bar chính), câu dài khi đủ chỗ. */}
      <span className="truncate lg:hidden">{t("network.offline")}</span>
      <span className="hidden truncate lg:inline">{t("offlinePage.statusOffline")}</span>
    </div>
  );
}
