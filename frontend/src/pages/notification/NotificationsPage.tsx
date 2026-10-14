import { useMemo, useRef, useState } from "react";
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
  Inbox,
  Loader2,
  PartyPopper,
  Ticket,
  Wrench,
  X,
  type LucideIcon,
} from "lucide-react";
import { Badge, EmptyState, ErrorState, Pagination, SkeletonLoader } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { useSessionStore } from "@/entities/user";
import {
  useHideNotification,
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotificationInbox,
  useUnreadNotificationCount,
  type NotificationItem,
} from "@/features/notification";


type InboxTab = "all" | "unread";

const PER_PAGE = 8;

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
  const [page, setPage] = useState(0);
  const listTopRef = useRef<HTMLDivElement>(null);
  const signedIn = Boolean(useSessionStore((s) => s.user));

  const inbox = useNotificationInbox(signedIn);
  const unread = useUnreadNotificationCount(signedIn);
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();
  const hide = useHideNotification();

  const items = useMemo(() => inbox.data?.pages.flatMap((page) => page.items) ?? [], [inbox.data]);

  const visible = useMemo(() => (tab === "unread" ? items.filter((item) => !item.read) : items), [items, tab]);

  const pageCount = Math.max(1, Math.ceil(visible.length / PER_PAGE));
  const currentPage = Math.min(page, pageCount - 1);
  const pageItems = useMemo(
    () => visible.slice(currentPage * PER_PAGE, (currentPage + 1) * PER_PAGE),
    [visible, currentPage],
  );

  const goToPage = (next: number) => {
    const scrollUp = () => {
      listTopRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    };
    if (next < pageCount) {
      setPage(next);
      scrollUp();
      return;
    }
    if (inbox.hasNextPage) {
      void inbox.fetchNextPage().then(() => {
        setPage(next);
        scrollUp();
      });
    }
  };

  const dayGroups = useMemo(() => {
    const map = new Map<string, NotificationItem[]>();
    pageItems.forEach((item) => {
      const key = dayGroupKey(item.createdAt);
      const list = map.get(key) ?? [];
      list.push(item);
      map.set(key, list);
    });
    return [...map.entries()];
  }, [pageItems]);

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
    <div className="mx-auto flex max-w-5xl flex-col gap-6 px-4 py-5 lg:px-0 lg:py-0">
      <header data-reveal>
        <div>
          <p className="mb-1 text-[11px] font-bold uppercase tracking-[0.16em] text-primary/70">{t("common:app.wordmark")}</p>
          <h1 className="text-[28px] font-bold leading-tight tracking-[-0.04em] text-text-primary lg:text-h1">
            {t("pages.list.title")}
          </h1>
          <p className="max-w-xl pt-2 text-body leading-relaxed text-text-secondary">{t("inbox.lead")}</p>
        </div>
      </header>

      <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-5">
          <div className="flex flex-col items-stretch gap-2.5 sm:flex-row sm:items-center sm:justify-between" data-reveal>
            <div
              className="flex w-full items-center gap-1 rounded-2xl border border-border/40 bg-background-alt/80 p-1 shadow-xs backdrop-blur-xs sm:w-fit"
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
                    setPage(0);
                  }}
                  className={cn(
                    "tab-pill-animated cursor-pointer min-h-10 flex-1 rounded-xl px-4 text-caption font-semibold transition-all duration-200 active:scale-[0.97] sm:flex-none",
                    tab === option.value
                      ? "bg-primary-dark font-bold text-white shadow-[0_4px_12px_rgba(13,54,154,0.25),inset_0_1px_0_rgba(255,255,255,0.2)]"
                      : "text-text-secondary hover:bg-surface hover:text-text-primary",
                  )}
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
              className="flex min-h-10 w-full items-center justify-start gap-2 rounded-xl px-3 text-caption font-semibold text-primary transition-colors hover:bg-primary/5 disabled:text-text-tertiary disabled:hover:bg-transparent sm:w-auto sm:justify-center"
            >
              {markAllRead.isPending ? (
                <Loader2 size={15} className="animate-spin" aria-hidden="true" />
              ) : (
                <CheckCheck size={15} aria-hidden="true" />
              )}
              {t("inbox.markAllRead")}
            </button>
          </div>

          <div ref={listTopRef} key={`${tab}-${String(currentPage)}`} className="tab-content-enter flex scroll-mt-20 flex-col gap-5">
            {inbox.isPending && signedIn ? (
              <div className="flex flex-col gap-2" aria-busy="true">
                <SkeletonLoader className="h-20 rounded-xl" />
                <SkeletonLoader className="h-20 rounded-xl" />
                <SkeletonLoader className="h-20 rounded-xl" />
              </div>
            ) : null}

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

            {dayGroups.map(([key, group]) => (
              <section key={key} className="flex flex-col gap-2">
                <p className="px-1 text-caption font-semibold text-text-tertiary">{dayLabel(key)}</p>
                <ul className="flex flex-col gap-2">
                  {group.map((item) => {
                    const Icon = TEMPLATE_ICONS[item.templateCode] ?? Bell;
                    return (
                      <li
                        key={item.id}
                        className="motion-card group flex items-start gap-3 rounded-2xl border border-primary/10 bg-surface p-4 shadow-[0_6px_18px_rgba(47,79,178,0.05)]"
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

            {inbox.isSuccess && visible.length === 0 ? (
              <EmptyState
                icon={<Inbox size={22} aria-hidden="true" />}
                title={tab === "unread" ? t("inbox.emptyUnreadTitle") : t("inbox.emptyTitle")}
                description={tab === "unread" ? t("inbox.emptyUnreadBody") : t("inbox.emptyBody")}
                className="rounded-[28px] border border-primary/10 bg-gradient-to-br from-surface via-surface to-info/35 shadow-[0_14px_34px_rgba(47,79,178,0.08)]"
              />
            ) : null}

            {visible.length > 0 ? (
              <div className="flex flex-col items-center gap-2 pt-1">
                <Pagination
                  page={currentPage}
                  pageCount={pageCount}
                  hasMore={inbox.hasNextPage}
                  loading={inbox.isFetchingNextPage}
                  onPageChange={goToPage}
                  labels={{
                    nav: t("inbox.pagination.nav"),
                    previous: t("inbox.pagination.previous"),
                    next: t("inbox.pagination.next"),
                    page: (n) => t("inbox.pagination.page", { page: n }),
                  }}
                />
                <p className="text-small text-text-tertiary">
                  {t(inbox.hasNextPage ? "inbox.pagination.rangeMore" : "inbox.pagination.range", {
                    from: currentPage * PER_PAGE + 1,
                    to: currentPage * PER_PAGE + pageItems.length,
                    total: visible.length,
                  })}
                </p>
              </div>
            ) : null}
          </div>
        </div>

        <aside className="flex w-full flex-col gap-4 rounded-[28px] border border-primary/10 bg-surface p-5 shadow-[0_12px_30px_rgba(47,79,178,0.07)] lg:w-[360px] lg:shrink-0" data-reveal>
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
