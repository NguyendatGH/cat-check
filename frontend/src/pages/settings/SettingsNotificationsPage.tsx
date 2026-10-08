import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Bell, ChevronRight, Smartphone, Timer } from "lucide-react";
import { PushDevicesCard } from "@/features/notification";
import { NotificationPreferencesForm } from "./NotificationPreferencesForm";
import { useScanReminderSummary } from "./settingsSummaries";

/**
 * `/settings/notifications` — kênh + loại thông báo.
 *
 * Phần bật/tắt là `NotificationPreferencesForm` dùng chung với khối tương ứng ở `/settings`
 * desktop (khối "Thông báo và cảnh báo") — một nguồn sự thật, lưu thật qua
 * `PUT /account/notification-preferences` (B12).
 *
 * Cột phải: thiết bị nhận push (G9–G11), lối sang hướng dẫn cài app và lịch nhắc (số lịch đang
 * bật đọc từ `GET /reminders`). Khối "Ngưỡng cảnh báo" cũ (ba ô tick đặt cứng) đã bỏ: không
 * endpoint nào lưu ngưỡng — dải tham chiếu thuộc `GET /reference/ph-bands`, không chỉnh được.
 *
 * Trang nằm trong `TaskLayout`: layout cấp `px-4 py-6` + hộp 944px, trang không tự thêm.
 */
export function SettingsNotificationsPage() {
  const { t } = useTranslation("settings");
  const reminders = useScanReminderSummary();

  const reminderNote =
    reminders.status !== "ready"
      ? t("mobile.rows.reminders.note")
      : reminders.activeCount === 0
        ? t("web.remindersNone")
        : t("web.remindersActive", { count: reminders.activeCount });

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="flex items-center gap-2.5 text-h2 font-bold text-text-primary lg:text-h1">
          <Bell size={22} className="shrink-0 text-primary-dark" aria-hidden="true" />
          {t("pages.notifications.title")}
        </h1>
        <p className="pt-1 text-body text-text-secondary">{t("notifications.intro")}</p>
      </header>

      <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:gap-6">
        <section className="min-w-0 flex-1 rounded-2xl bg-surface p-5 shadow-brand-md">
          <NotificationPreferencesForm />
        </section>

        <div className="flex w-full flex-col gap-4 xl:w-[360px] xl:shrink-0">
          {/*
            Thiết bị nhận push (G9/G10/G11). Đặt ở ĐÂY chứ không ở `/notifications` theo
            p12 §12.3.4: CTA bật push phải nằm trong cài đặt, không nhét vào hộp thư.
          */}
          <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
            <PushDevicesCard />
          </section>

          <Link
            to="/install"
            className="flex items-center gap-3 rounded-2xl bg-surface p-4 shadow-brand-md hover:bg-background-alt"
          >
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-chip-bg text-primary-dark">
              <Smartphone size={18} aria-hidden="true" />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-body font-bold text-text-primary">
                {t("notifications.installLink.title")}
              </span>
              <span className="block text-caption text-text-secondary">{t("notifications.installLink.body")}</span>
            </span>
            <ChevronRight size={17} className="shrink-0 text-text-tertiary" aria-hidden="true" />
          </Link>

          <Link
            to="/reminders"
            className="flex items-center gap-3 rounded-2xl bg-surface p-4 shadow-brand-md hover:bg-background-alt"
          >
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-chip-bg text-primary-dark">
              <Timer size={18} aria-hidden="true" />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-body font-bold text-text-primary">{t("mobile.rows.reminders.title")}</span>
              <span className="block text-caption text-text-secondary">{reminderNote}</span>
            </span>
            <ChevronRight size={17} className="shrink-0 text-text-tertiary" aria-hidden="true" />
          </Link>
        </div>
      </div>
    </div>
  );
}
