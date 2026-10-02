import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { format, isToday, isYesterday } from "date-fns";
import { AlertTriangle, BellOff, CalendarClock, FileDown, Inbox, Ticket } from "lucide-react";
import { Badge } from "@/shared/ui";

/**
 * `/notifications` — hộp thư thông báo trong ứng dụng. Vào từ icon chuông ở header
 * (`AppLayout` mobile và desktop đều trỏ `ROUTE_PATTERNS.notifications`), nên đây là một
 * nhánh điều hướng chính chứ không phải màn phụ.
 *
 * CHƯA CÓ API — đã kiểm chứng, không suy đoán:
 * - Bảng `notification` ĐÃ tồn tại: `backend/.../db/migration/V13__notification.sql` (p4 F2),
 *   kèm index `(user_id) WHERE read_at IS NULL AND channel = 'IN_APP'` dành riêng cho badge
 *   số chưa đọc trên icon chuông.
 * - Nhưng `backend/src/main/java/com/catcheck/notification` CHỈ có `EmailSender`/
 *   `SmtpEmailSender`/`FileEmailSender` — không có controller, không có endpoint đọc. Chính
 *   V13 cũng ghi: "PHẠM VI JAVA: M1 chỉ hiện thực module `reminder` … năm bảng còn lại được
 *   tạo DDL đầy đủ ở đây … nhưng chưa có code Java đọc/ghi".
 *
 * Vì vậy trang dựng đủ khung hộp thư (tab chưa đọc/tất cả, gom nhóm theo ngày, bốn loại
 * thông báo của p4 F2) nhưng danh sách là mảng RỖNG THẬT — không bịa dữ liệu mẫu, không gọi
 * API không tồn tại, và nói thẳng với người dùng rằng hộp thư chưa nối máy chủ.
 *
 * Trang nằm trong `TaskLayout`: layout đã cấp `px-4 py-6` + hộp 944px, trang không tự thêm.
 */

type InboxTab = "all" | "unread";

/** Khớp các cột `notification` của p4 F2 — hình dạng để sẵn cho lúc endpoint mở. */
interface NotificationInboxItem {
  id: string;
  templateCode: string;
  titleSnapshot: string;
  bodySnapshot: string;
  createdAt: string;
  readAt: string | null;
}

/**
 * Bốn `template_code` liệt kê ở p4 F2, theo thứ tự của bảng đó. Hiện dùng để giải thích cho
 * người dùng những gì sẽ về hộp thư này; khi có endpoint thì cũng là bản đồ icon theo loại.
 */
const NOTIFICATION_KINDS = [
  { code: "health_flag.repeated_out_of_range", key: "healthFlag", icon: AlertTriangle },
  { code: "credit.expiring_48h", key: "credit", icon: Ticket },
  { code: "reminder.scan_due", key: "reminder", icon: CalendarClock },
  { code: "export.ready", key: "export", icon: FileDown },
] as const;

function dayGroupKey(iso: string): string {
  return format(new Date(iso), "yyyy-MM-dd");
}

export function NotificationsPage() {
  const { t } = useTranslation(["notification", "common"]);
  const [tab, setTab] = useState<InboxTab>("all");

  /**
   * Nguồn dữ liệu của hộp thư. Rỗng vì chưa có endpoint (xem ghi chú đầu file) — khi
   * `GET /notifications` mở thì thay bằng hook react-query, phần render bên dưới giữ nguyên.
   */
  const items: NotificationInboxItem[] = useMemo(() => [], []);

  const visible = useMemo(
    () => (tab === "unread" ? items.filter((item) => item.readAt === null) : items),
    [items, tab],
  );

  const dayGroups = useMemo(() => {
    const map = new Map<string, NotificationInboxItem[]>();
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
          <div className="inline-flex items-center gap-1 self-start rounded-lg bg-background-alt p-1" role="tablist">
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
              </button>
            ))}
          </div>

          {dayGroups.map(([key, group]) => (
            <section key={key} className="flex flex-col gap-2">
              <p className="px-1 text-caption font-semibold text-text-tertiary">{dayLabel(key)}</p>
              <ul className="flex flex-col gap-2">
                {group.map((item) => (
                  <li
                    key={item.id}
                    className="flex items-start gap-3 rounded-xl border border-border bg-surface p-3"
                  >
                    <Inbox size={18} className="mt-0.5 shrink-0 text-primary-dark" aria-hidden="true" />
                    <span className="flex min-w-0 flex-1 flex-col gap-1">
                      <span className="flex flex-wrap items-center gap-2">
                        <span className="text-body font-semibold text-text-primary">{item.titleSnapshot}</span>
                        {item.readAt === null ? <Badge tone="brand">{t("inbox.unreadBadge")}</Badge> : null}
                      </span>
                      <span className="text-caption text-text-secondary">{item.bodySnapshot}</span>
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          ))}

          {/* ---------- Trạng thái thật: hộp thư chưa nối máy chủ ---------- */}
          <section className="flex flex-col items-center gap-2 rounded-2xl bg-surface px-6 py-10 text-center shadow-brand-md">
            <span
              className="flex size-12 items-center justify-center rounded-full bg-background-alt text-text-tertiary"
              aria-hidden="true"
            >
              <BellOff size={22} />
            </span>
            <h2 className="text-h3 font-bold text-text-primary">{t("inbox.noApiTitle")}</h2>
            <p className="max-w-md text-caption leading-relaxed text-text-secondary">{t("inbox.noApiBody")}</p>
            <p className="max-w-md text-caption leading-relaxed text-text-tertiary">{t("inbox.noApiHonesty")}</p>
          </section>
        </div>

        {/* ---------- Bốn loại thông báo (p4 F2) ---------- */}
        <aside className="flex w-full flex-col gap-3 rounded-2xl bg-surface p-5 shadow-brand-md lg:w-[360px] lg:shrink-0">
          <h2 className="text-h3 font-bold text-text-primary">{t("inbox.kindsTitle")}</h2>
          <p className="text-caption leading-relaxed text-text-secondary">{t("inbox.kindsLead")}</p>
          <ul className="flex flex-col gap-3">
            {NOTIFICATION_KINDS.map(({ code, key, icon: Icon }) => (
              <li key={code} className="flex items-start gap-3">
                <span
                  className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-background-alt text-primary-dark"
                  aria-hidden="true"
                >
                  <Icon size={17} />
                </span>
                <span className="flex min-w-0 flex-col">
                  <span className="text-body font-semibold text-text-primary">
                    {t(`inbox.kinds.${key}.title`)}
                  </span>
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
