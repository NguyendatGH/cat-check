import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Bell, ChevronRight, Smartphone, Timer } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { PushDevicesCard } from "@/features/notification";
import { NotificationPreferencesForm } from "./NotificationPreferencesForm";
import { SETTINGS_ALERT_THRESHOLDS } from "./mockData";

/**
 * `/settings/notifications` — kênh + loại thông báo.
 *
 * Phần bật/tắt là `NotificationPreferencesForm` dùng chung với khối tương ứng ở `/settings`
 * desktop (`Web - 16`, "Cấu hình Lịch nhắc & Cảnh báo Sức khỏe") — một nguồn sự thật.
 * Danh sách ngưỡng báo động bên dưới là nội dung của chính bản thiết kế; hai mục không nằm
 * trong phạm vi sản phẩm được render tắt kèm nhãn, xem `mockData.ts`.
 *
 * Trang nằm trong `TaskLayout`: layout cấp `px-4 py-6` + hộp 944px, trang không tự thêm.
 */
export function SettingsNotificationsPage() {
  const { t } = useTranslation("settings");

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="flex items-center gap-2.5 text-h2 font-bold text-text-primary lg:text-h1">
          <Bell size={22} className="text-primary-dark" aria-hidden="true" />
          {t("pages.notifications.title")}
        </h1>
        <p className="pt-1 text-body text-text-secondary">{t("notifications.intro")}</p>
      </header>

      <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-5">
          <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
            <NotificationPreferencesForm />
          </section>

          {/*
            Thiết bị nhận push (G9/G10/G11). Đặt ở ĐÂY chứ không ở `/notifications` theo
            p12 §12.3.4: CTA bật push phải nằm trong cài đặt, không nhét vào hộp thư.
          */}
          <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
            <PushDevicesCard />
          </section>
        </div>

        <div className="flex w-full flex-col gap-4 lg:w-[380px] lg:shrink-0">
          <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
            <h2 className="pb-3 text-[11px] font-bold uppercase tracking-wide text-text-tertiary">
              {t("web.thresholdsLegend")}
            </h2>
            <ul className="flex flex-col gap-2">
              {SETTINGS_ALERT_THRESHOLDS.map((item) => (
                <li key={item.key} className="flex items-start gap-3 rounded-xl bg-background-alt/60 p-3">
                  <span
                    aria-hidden="true"
                    className={cn(
                      "mt-0.5 flex size-4 shrink-0 rounded border",
                      item.supported ? "border-primary bg-primary" : "border-border-strong",
                    )}
                  />
                  <span className="min-w-0">
                    <span className="block text-caption font-semibold text-text-primary">{item.title}</span>
                    <span className="block pt-0.5 text-[11px] leading-relaxed text-text-tertiary">{item.body}</span>
                  </span>
                  {!item.supported ? (
                    <span className="ml-auto shrink-0 rounded-md bg-surface px-2 py-0.5 text-[10px] font-semibold text-text-tertiary">
                      {t("web.outOfScope")}
                    </span>
                  ) : null}
                </li>
              ))}
            </ul>
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
              <span className="block text-caption text-text-secondary">{t("mobile.rows.reminders.note")}</span>
            </span>
            <ChevronRight size={17} className="shrink-0 text-text-tertiary" aria-hidden="true" />
          </Link>
        </div>
      </div>
    </div>
  );
}
