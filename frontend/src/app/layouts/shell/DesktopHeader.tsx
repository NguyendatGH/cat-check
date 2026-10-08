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

/**
 * Pill trạng thái ở header (vị trí chip "Hệ vi sinh & pH ổn định" của Figma 16:7475).
 *
 * Chip của thiết kế là một tuyên bố sức khoẻ TĨNH, sáng xanh ở mọi trang dù dữ liệu ra sao.
 * Nay nó là lần quét gần nhất thật: `GET /scans` trang đầu (CÙNG query key với Trang chủ và
 * Lịch sử nên không thêm request) + nhãn/màu của dải từ `GET /reference/ph-bands`. Chưa có lần
 * quét nào ⇒ không hiện gì, không dựng câu thay thế.
 */
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

/**
 * Header desktop — Figma node 16:7475, cao đúng 64px: ô tìm kiếm · pill lần quét gần nhất ·
 * chuông · user. `onBack` chỉ TaskLayout dùng: trang tác vụ (sửa hồ sơ, chi tiết lần quét…)
 * vẫn cần đường quay lại khi đã có sidebar.
 */
export function DesktopHeader({ showBack = false }: { showBack?: boolean }) {
  const { t } = useTranslation("common");
  const navigate = useNavigate();
  const user = useSessionStore((s) => s.user);

  return (
    <header className="sticky top-0 z-[var(--z-sticky-header)] hidden h-16 w-full shrink-0 items-center gap-4 border-b border-border bg-surface px-6 lg:flex">
      {showBack ? (
        <button
          type="button"
          onClick={() => {
            void navigate(-1);
          }}
          aria-label={t("actions.back")}
          className="flex size-10 shrink-0 items-center justify-center rounded-xl text-text-secondary hover:bg-background-alt"
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
          className="relative flex size-10 items-center justify-center rounded-xl hover:bg-background-alt"
        >
          <img src={iconBell} alt="" className="h-[16.667px] w-[13.333px]" />
          {/* Số chưa đọc thật từ G5 `GET /notifications/unread-count`, tự ẩn khi bằng 0. */}
          <NotificationUnreadBadge enabled={Boolean(user)} />
        </button>

        {/* Trang chủ xem được ở chế độ khách — chưa đăng nhập thì hiện CTA đăng nhập thay vì ô
            hồ sơ (bấm vào sẽ bị RequireAuth đá về login, gây cụt luồng). */}
        {user ? (
          <button
            type="button"
            onClick={() => {
              void navigate(ROUTE_PATTERNS.settingsProfile);
            }}
            className="flex max-w-[260px] items-center gap-3 rounded-xl py-1 pl-2 hover:bg-background-alt"
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
            className="flex h-9 items-center justify-center rounded-xl bg-primary px-4 text-caption font-semibold text-white"
          >
            {t("dashboard.guestBanner.login")}
          </button>
        )}
      </div>
    </header>
  );
}
