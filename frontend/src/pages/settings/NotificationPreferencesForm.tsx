import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertTriangle, BellRing, Check, Clock, Loader2, Smartphone } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { Switch } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import {
  useNotificationPreferences,
  useUpdateNotificationPreferences,
  type AttentionAlertChannel,
  type NotificationPreferences,
  type NotificationToggleKey,
} from "@/features/settings";

/**
 * Khối tuỳ chọn thông báo — dùng CHUNG bởi `/settings` (desktop, khối "Cấu hình Lịch nhắc &
 * Cảnh báo Sức khoẻ" trong Web-16) và `/settings/notifications` (màn riêng bản mobile), để
 * hai nơi không trôi khỏi nhau.
 *
 * Nối API thật `GET|PUT /account/notification-preferences` (B11/B12), shape tám field phẳng
 * theo p8 §8.5 — xem `features/settings/types.ts` để biết vì sao không phải shape JSONB cũ.
 *
 * `PUT` thay TOÀN BỘ biểu diễn (p8 §8.1.11): mỗi lần gạt một công tắc vẫn phải gửi đủ tám
 * field, nên `commit` luôn trải `draft` ra chứ không gửi patch.
 */

/** Bốn công tắc boolean độc lập, theo thứ tự hiển thị của Web-16. */
const TOGGLES: NotificationToggleKey[] = [
  "creditAlertsEnabled",
  "reportReadyEnabled",
  "normalResultEnabled",
  "imageRetentionWarningEnabled",
];

const ALERT_CHANNELS: AttentionAlertChannel[] = ["PUSH_AND_INAPP", "INAPP_ONLY"];

export function NotificationPreferencesForm({ className }: { className?: string }) {
  const { t } = useTranslation(["settings", "common"]);
  const query = useNotificationPreferences();
  const update = useUpdateNotificationPreferences();
  const [draft, setDraft] = useState<NotificationPreferences | null>(null);

  useEffect(() => {
    if (query.data) setDraft(query.data);
  }, [query.data]);

  if (query.isPending) {
    return (
      <p className={cn("flex items-center gap-2 text-caption text-text-secondary", className)}>
        <Loader2 size={15} className="animate-spin" aria-hidden="true" />
        {t("common:actions.loading")}
      </p>
    );
  }

  if (query.isError || !draft) {
    const unavailable =
      isApiError(query.error) && (query.error.status === 403 || query.error.status === 501);
    return (
      <p
        className={cn(
          "flex items-start gap-2 rounded-xl bg-warning-bg px-3.5 py-2.5 text-caption text-warning-text",
          className,
        )}
      >
        <AlertTriangle size={15} className="mt-0.5 shrink-0" aria-hidden="true" />
        {unavailable ? t("notifications.unavailable") : t("notifications.saveFailed")}
      </p>
    );
  }

  const commit = (next: NotificationPreferences) => {
    setDraft(next);
    update.mutate(next);
  };

  return (
    <div className={cn("flex flex-col gap-5", className)}>
      <fieldset className="flex flex-col gap-2.5">
        <legend className="pb-1 text-caption font-semibold uppercase tracking-wide text-text-tertiary">
          {t("notifications.alertChannelLegend")}
        </legend>
        {/*
          Không có công tắc "tắt" ở đây, và đó là chủ đích: cảnh báo kết quả cần chú ý là lý
          do tồn tại của sản phẩm (quyết định #10), p4 F4 chỉ cho chọn kênh. Server trả 400
          cho mọi giá trị ngoài hai cái này.
        */}
        <p className="text-caption text-text-secondary">{t("notifications.alertChannelNote")}</p>
        <div className="flex flex-col gap-2">
          {ALERT_CHANNELS.map((channel) => {
            const selected = draft.attentionAlertChannel === channel;
            return (
              <label
                key={channel}
                className={cn(
                  "flex cursor-pointer items-center gap-3 rounded-xl border p-3 transition-colors",
                  selected
                    ? "border-primary bg-chip-bg/40"
                    : "border-border bg-background-alt/50 hover:border-border-strong",
                )}
              >
                <input
                  type="radio"
                  name="attentionAlertChannel"
                  value={channel}
                  checked={selected}
                  onChange={() => { commit({ ...draft, attentionAlertChannel: channel }); }}
                  className="sr-only"
                />
                <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface text-primary-dark">
                  {channel === "PUSH_AND_INAPP" ? (
                    <Smartphone size={17} aria-hidden="true" />
                  ) : (
                    <BellRing size={17} aria-hidden="true" />
                  )}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block text-body font-semibold text-text-primary">
                    {t(`notifications.alertChannels.${channel}.title`)}
                  </span>
                  <span className="block text-caption text-text-secondary">
                    {t(`notifications.alertChannels.${channel}.body`)}
                  </span>
                </span>
                {selected ? (
                  <Check size={16} className="shrink-0 text-primary-dark" aria-hidden="true" />
                ) : null}
              </label>
            );
          })}
        </div>
      </fieldset>

      <fieldset className="flex flex-col gap-2.5">
        <legend className="pb-1 text-caption font-semibold uppercase tracking-wide text-text-tertiary">
          {t("notifications.typesLegend")}
        </legend>
        {TOGGLES.map((key) => (
          <div
            key={key}
            className="flex items-center gap-3 rounded-xl border border-border bg-background-alt/50 p-3"
          >
            <span className="min-w-0 flex-1">
              <span className="block text-body font-semibold text-text-primary">
                {t(`notifications.toggles.${key}.title`)}
              </span>
              <span className="block text-caption text-text-secondary">
                {t(`notifications.toggles.${key}.body`)}
              </span>
            </span>
            <Switch
              checked={draft[key]}
              onCheckedChange={(checked) => { commit({ ...draft, [key]: checked }); }}
              aria-label={t(`notifications.toggles.${key}.title`)}
            />
          </div>
        ))}
      </fieldset>

      <fieldset className="flex flex-col gap-2.5">
        <legend className="pb-1 text-caption font-semibold uppercase tracking-wide text-text-tertiary">
          {t("notifications.quietHoursLegend")}
        </legend>
        <div className="flex items-center gap-3 rounded-xl border border-border bg-background-alt/50 p-3">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface text-primary-dark">
            <Clock size={17} aria-hidden="true" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-body font-semibold text-text-primary">
              {t("notifications.quietHours.title")}
            </span>
            <span className="block text-caption text-text-secondary">
              {t("notifications.quietHours.range", {
                start: draft.quietHoursStart,
                end: draft.quietHoursEnd,
              })}
            </span>
          </span>
          <Switch
            checked={draft.quietHoursEnabled}
            onCheckedChange={(checked) => { commit({ ...draft, quietHoursEnabled: checked }); }}
            aria-label={t("notifications.quietHours.title")}
          />
        </div>
        {draft.quietHoursEnabled ? (
          <div className="flex items-center gap-3">
            {(["quietHoursStart", "quietHoursEnd"] as const).map((field) => (
              <label key={field} className="flex min-w-0 flex-1 flex-col gap-1">
                <span className="text-caption text-text-secondary">
                  {t(`notifications.quietHours.${field}`)}
                </span>
                <input
                  type="time"
                  value={draft[field]}
                  onChange={(event) => { commit({ ...draft, [field]: event.target.value }); }}
                  className="h-11 rounded-xl border border-border bg-surface px-3 text-body text-text-primary"
                />
              </label>
            ))}
          </div>
        ) : null}
        {/* `start > end` là hợp lệ (vắt qua nửa đêm) — đừng thêm validation chặn nó. */}
      </fieldset>

      <p
        aria-live="polite"
        className={cn(
          "flex items-center gap-1.5 text-caption",
          update.isError ? "text-danger" : "text-success-text",
        )}
      >
        {update.isPending ? (
          <>
            <Loader2 size={14} className="animate-spin" aria-hidden="true" />
            {t("notifications.saving")}
          </>
        ) : update.isError ? (
          <>
            <AlertTriangle size={14} aria-hidden="true" />
            {t("notifications.saveFailed")}
          </>
        ) : update.isSuccess ? (
          <>
            <Check size={14} aria-hidden="true" />
            {t("notifications.saved")}
          </>
        ) : null}
      </p>
    </div>
  );
}
