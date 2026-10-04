import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { format, isToday, isYesterday } from "date-fns";
import {
  AlertTriangle,
  Bell,
  CalendarClock,
  CheckCheck,
  FileDown,
  Gift,
  ImageOff,
  Loader2,
  PartyPopper,
  Ticket,
  Wrench,
  X,
  type LucideIcon,
} from "lucide-react";
import { Badge, EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { useSessionStore } from "@/entities/user";
import {
  useHideNotification,
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotificationInbox,
  useUnreadNotificationCount,
  type NotificationItem,
} from "@/features/notification";

/**
 * `/notifications` — hộp thư thông báo trong ứng dụng. Vào từ icon chuông ở header
 * (`AppLayout` mobile và desktop đều trỏ `ROUTE_PATTERNS.notifications`), nên đây là một
 * nhánh điều hướng chính chứ không phải màn phụ.
 *
 * ĐÃ NỐI API THẬT (p8 §8.4.7 G4–G8) — 5 endpoint của `NotificationController`:
 * danh sách cursor, số chưa đọc, đánh dấu đã đọc một/tất cả, ẩn khỏi hộp thư.
 * Bố cục giữ nguyên bản kiểm kê thiết kế `X1 §9` (2 cột ở web, 1 cột ở mobile); khối "chưa
 * nối máy chủ" của bản cũ được thay bằng các trạng thái thật: đang tải / lỗi / rỗng /
 * "chưa đọc" rỗng.
 *
 * Hai điểm hợp đồng khác spec, đã ghi handoff:
 * - `templateCode` là TÊN ENUM (`REMINDER_SCAN_DUE`), không phải `reminder.scan_due` như p4 F2
 *   / X1 mô tả ⇒ bảng icon dưới đây khớp theo tên enum (H15.108).
 * - Không có template `health_flag.*`: thứ gần nhất là `SCAN_RESULT_ATTENTION` (H15.109).
 *
 * Trang nằm trong `TaskLayout`: layout đã cấp `px-4 py-6` + hộp 944px, trang không tự thêm.
 */

type InboxTab = "all" | "unread";

/**
 * Icon theo `template_code`. Khoá là tên hằng `NotificationTemplate` của backend — chỉ liệt
 * kê 12 template có `writesInAppRecord = true` (2 template OTP và marketing không bao giờ
 * vào hộp thư in-app). Template lạ rơi về `Bell`, không vỡ trang.
 */
const TEMPLATE_ICONS: Record<string, LucideIcon> = {
  WELCOME_ONBOARDING: PartyPopper,
  REMINDER_SCAN_DUE: CalendarClock,
  REMINDER_OVERDUE: CalendarClock,
  SCAN_RESULT_ATTENTION: AlertTriangle,
  SCAN_RESULT_NORMAL: Bell,
  CREDIT_EXPIRING_T48H: Ticket,
  CREDIT_EXPIRING_T6H: Ticket,
  CREDIT_EXPIRED: Ticket,
  ACTIVATION_SUCCESS: Gift,
  REPORT_PDF_READY: FileDown,
  IMAGE_RETENTION_WARNING: ImageOff,
  SYSTEM_MAINTENANCE: Wrench,
};

/**
 * Bốn loại thông báo giới thiệu ở cột phải — giữ đúng bản kiểm kê X1 §9. Đây là nội dung
 * tĩnh (giải thích "những gì sẽ về hộp thư này"), KHÔNG phải dữ liệu giả thay cho API.
 */
const NOTIFICATION_KINDS = [
  { key: "healthFlag", icon: AlertTriangle },
  { key: "credit", icon: Ticket },
  { key: "reminder", icon: CalendarClock },
  { key: "export", icon: FileDown },
] as const;

function dayGroupKey(iso: string): string {
  return format(new Date(iso), "yyyy-MM-dd");
}

export function NotificationsPage() {
  const { t } = useTranslation(["notification", "common"]);
  const navigate = useNavigate();
  const [tab, setTab] = useState<InboxTab>("all");
  const signedIn = Boolean(useSessionStore((s) => s.user));

  const inbox = useNotificationInbox(signedIn);
  const unread = useUnreadNotificationCount(signedIn);
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();
  const hide = useHideNotification();

  const items = useMemo(() => inbox.data?.pages.flatMap((page) => page.items) ?? [], [inbox.data]);

  /**
   * Lọc "Chưa đọc" ở CLIENT theo đúng X1 §9 ("lọc client, không đổi URL") — G4 không có tham
   * số `read`. Hệ quả phải biết: bộ lọc chỉ thấy những trang ĐÃ tải, nên khi còn trang sau,
   * tab "Chưa đọc" rỗng không có nghĩa là không còn thông báo chưa đọc nào. Số thật luôn nằm
   * ở badge (`unread-count`), và nút "Tải thêm" vẫn hiện để kéo tiếp.
   */
  const visible = useMemo(() => (tab === "unread" ? items.filter((item) => !item.read) : items), [items, tab]);

  const dayGroups = useMemo(() => {
    const map = new Map<string, NotificationItem[]>();
    visible.forEach((item) => {
      const key = dayGroupKey(item.createdAt);
      const list = map.get(key) ?? [];
      list.push(item);
      map.set(key, list);
    });
    return [...map.entries()];
  }, [visible]);

  const dayLabel = (key: string): string => {
    const date = new Date(key);
    if (isToday(date)) return t("inbox.groupToday");
    if (isYesterday(date)) return t("inbox.groupYesterday");
    return format(date, "dd/MM/yyyy");
  };

  const unreadCount = unread.data?.unreadCount ?? 0;

  const openNotification = (item: NotificationItem) => {
    if (!item.read) markRead.mutate(item.id);
    if (item.deepLink) void navigate(item.deepLink);
  };

  const tabs: { value: InboxTab; label: string }[] = [
    { value: "all", label: t("inbox.tabAll") },
    { value: "unread", label: t("inbox.tabUnread") },
  ];

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("pages.list.title")}</h1>
        <p className="pt-1 text-body text-text-secondary">{t("inbox.lead")}</p>
      </header>

      <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div
              className="inline-flex items-center gap-1 self-start rounded-lg bg-background-alt p-1"
              role="tablist"
              aria-label={t("pages.list.title")}
            >
              {tabs.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  role="tab"
                  aria-selected={tab === option.value}
                  onClick={() => {
                    setTab(option.value);
                  }}
                  className={
                    tab === option.value
                      ? "min-h-[var(--touch-target-min)] rounded-md bg-surface px-4 text-caption font-semibold text-primary shadow-xs"
                      : "min-h-[var(--touch-target-min)] rounded-md px-4 text-caption font-semibold text-text-secondary"
                  }
                >
                  {option.label}
                  {option.value === "unread" && unreadCount > 0 ? ` (${String(unreadCount)})` : ""}
                </button>
              ))}
            </div>

            <button
              type="button"
              disabled={unreadCount === 0 || markAllRead.isPending}
              onClick={() => {
                markAllRead.mutate();
              }}
              className="flex min-h-11 items-center gap-2 rounded-xl px-3 text-caption font-semibold text-primary hover:bg-background-alt disabled:text-text-tertiary disabled:hover:bg-transparent"
            >
              {markAllRead.isPending ? (
                <Loader2 size={15} className="animate-spin" aria-hidden="true" />
              ) : (
                <CheckCheck size={15} aria-hidden="true" />
              )}
              {t("inbox.markAllRead")}
            </button>
          </div>

          {/* ---------- đang tải lần đầu ---------- */}
          {inbox.isPending && signedIn ? (
            <div className="flex flex-col gap-2" aria-busy="true">
              <SkeletonLoader className="h-20 rounded-xl" />
              <SkeletonLoader className="h-20 rounded-xl" />
              <SkeletonLoader className="h-20 rounded-xl" />
            </div>
          ) : null}

          {/* ---------- lỗi ---------- */}
          {inbox.isError ? (
            <ErrorState
              title={t("inbox.errorTitle")}
              description={t("inbox.errorBody")}
              retryLabel={t("common:actions.retry")}
              onRetry={() => {
                void inbox.refetch();
              }}
            />
          ) : null}

          {/* ---------- danh sách ---------- */}
          {dayGroups.map(([key, group]) => (
            <section key={key} className="flex flex-col gap-2">
              <p className="px-1 text-caption font-semibold text-text-tertiary">{dayLabel(key)}</p>
              <ul className="flex flex-col gap-2">
                {group.map((item) => {
                  const Icon = TEMPLATE_ICONS[item.templateCode] ?? Bell;
                  return (
                    <li
                      key={item.id}
                      className="flex items-start gap-3 rounded-xl border border-border bg-surface p-3"
                      data-unread={item.read ? undefined : "true"}
                    >
                      <Icon size={18} className="mt-0.5 shrink-0 text-primary-dark" aria-hidden="true" />
                      <button
                        type="button"
                        onClick={() => {
                          openNotification(item);
                        }}
                        className="flex min-w-0 flex-1 flex-col gap-1 text-left"
                      >
                        <span className="flex flex-wrap items-center gap-2">
                          <span className="text-body font-semibold text-text-primary">{item.title}</span>
                          {item.read ? null : <Badge tone="brand">{t("inbox.unreadBadge")}</Badge>}
                        </span>
                        <span className="text-caption text-text-secondary">{item.body}</span>
                      </button>
                      <button
                        type="button"
                        disabled={hide.isPending}
                        onClick={() => {
                          hide.mutate(item.id);
                        }}
                        aria-label={t("inbox.hide")}
                        className="flex size-8 shrink-0 items-center justify-center rounded-lg text-text-tertiary hover:bg-background-alt hover:text-text-secondary disabled:opacity-50"
                      >
                        <X size={15} aria-hidden="true" />
                      </button>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}

          {/* ---------- rỗng ---------- */}
          {inbox.isSuccess && visible.length === 0 ? (
            <EmptyState
              title={tab === "unread" ? t("inbox.emptyUnreadTitle") : t("inbox.emptyTitle")}
              description={tab === "unread" ? t("inbox.emptyUnreadBody") : t("inbox.emptyBody")}
            />
          ) : null}

          {/* ---------- tải thêm ---------- */}
          {inbox.hasNextPage ? (
            <button
              type="button"
              disabled={inbox.isFetchingNextPage}
              onClick={() => {
                void inbox.fetchNextPage();
              }}
              className="flex min-h-11 items-center justify-center gap-2 self-center rounded-xl border border-border px-4 text-caption font-semibold text-primary hover:bg-background-alt disabled:opacity-60"
            >
              {inbox.isFetchingNextPage ? <Loader2 size={15} className="animate-spin" aria-hidden="true" /> : null}
              {t("inbox.loadMore")}
            </button>
          ) : null}
        </div>

        {/* ---------- Bốn loại thông báo (p4 F2) ---------- */}
        <aside className="flex w-full flex-col gap-3 rounded-2xl bg-surface p-5 shadow-brand-md lg:w-[360px] lg:shrink-0">
          <h2 className="text-h3 font-bold text-text-primary">{t("inbox.kindsTitle")}</h2>
          <p className="text-caption leading-relaxed text-text-secondary">{t("inbox.kindsLead")}</p>
          <ul className="flex flex-col gap-3">
            {NOTIFICATION_KINDS.map(({ key, icon: Icon }) => (
              <li key={key} className="flex items-start gap-3">
                <span
                  className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-background-alt text-primary-dark"
                  aria-hidden="true"
                >
                  <Icon size={17} />
                </span>
                <span className="flex min-w-0 flex-col">
                  <span className="text-body font-semibold text-text-primary">{t(`inbox.kinds.${key}.title`)}</span>
                  <span className="text-caption leading-relaxed text-text-secondary">
                    {t(`inbox.kinds.${key}.body`)}
                  </span>
                </span>
              </li>
            ))}
          </ul>
          <div className="flex flex-col gap-2 pt-1">
            <Link
              to="/settings/notifications"
              className="flex min-h-11 items-center justify-center rounded-xl border border-border px-4 text-caption font-semibold text-primary hover:bg-background-alt"
            >
              {t("inbox.settingsCta")}
            </Link>
            <Link
              to="/reminders"
              className="flex min-h-11 items-center justify-center rounded-xl px-4 text-caption font-semibold text-text-secondary hover:bg-background-alt"
            >
              {t("inbox.remindersCta")}
            </Link>
          </div>
        </aside>
      </div>
    </div>
  );
}
