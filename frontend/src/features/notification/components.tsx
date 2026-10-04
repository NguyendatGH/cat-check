import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { AlertTriangle, BellRing, Check, Laptop, Loader2, Smartphone, Trash2 } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { isApiError } from "@/shared/api";
import { Switch } from "@/shared/ui";
import {
  usePushSubscriptions,
  useRevokePushSubscription,
  useUnreadNotificationCount,
  useUpsertPushSubscription,
} from "./hooks";
import {
  PushRegistrationError,
  currentDeviceLabel,
  currentPlatform,
  pushAvailability,
  readLocalSubscriptionId,
  registerThisDevice,
  unregisterThisDevice,
  writeLocalSubscriptionId,
  type PushAvailability,
} from "./pushClient";
import type { PushSubscriptionItem } from "./types";

/** Quá 99 thì bong bóng badge vỡ bố cục header — và con số chính xác lúc đó cũng vô nghĩa. */
const BADGE_MAX = 99;

/**
 * Badge số chưa đọc trên icon chuông (G5).
 *
 * Đặt trong `features/` chứ không viết thẳng vào `AppLayout` vì có HAI cái chuông (header
 * mobile và header desktop) — nhân đôi code là cách chắc chắn nhất để hai chỗ trôi khỏi nhau.
 * Component tự ẩn khi `unreadCount = 0`: một chấm đỏ vĩnh viễn (hiện trạng trước gói này)
 * dạy người dùng bỏ qua nó.
 *
 * @param enabled `false` cho khách chưa đăng nhập — endpoint yêu cầu phiên.
 */
export function NotificationUnreadBadge({ enabled = true }: { enabled?: boolean }) {
  const { t } = useTranslation("notification");
  const { data } = useUnreadNotificationCount(enabled);
  const count = data?.unreadCount ?? 0;
  if (count <= 0) return null;
  const label = count > BADGE_MAX ? `${String(BADGE_MAX)}+` : String(count);
  return (
    <span
      data-testid="notification-unread-badge"
      aria-label={t("badge.unreadAria", { count })}
      className="absolute -right-0.5 -top-0.5 flex min-w-[18px] items-center justify-center rounded-full bg-danger px-1 text-[10px] font-bold leading-[18px] text-white"
    >
      {label}
    </span>
  );
}

// ------------------------------------------------------------------ thiết bị nhận push

function PushBlockedNotice({ availability }: { availability: Extract<PushAvailability, { available: false }> }) {
  const { t } = useTranslation("notification");
  return (
    <div className="flex items-start gap-2.5 rounded-xl bg-background-alt px-3.5 py-3">
      <AlertTriangle size={16} className="mt-0.5 shrink-0 text-text-tertiary" aria-hidden="true" />
      <span className="min-w-0 flex-1">
        <span className="block text-caption font-semibold text-text-primary">
          {t(`push.blocked.${availability.reason}.title`)}
        </span>
        <span className="block pt-0.5 text-caption leading-relaxed text-text-secondary">
          {t(`push.blocked.${availability.reason}.body`)}
        </span>
        {availability.reason === "IOS_NEEDS_INSTALL" ? (
          <Link to="/install" className="inline-flex pt-1.5 text-caption font-semibold text-primary underline">
            {t("push.blocked.IOS_NEEDS_INSTALL.cta")}
          </Link>
        ) : null}
      </span>
    </div>
  );
}

function DeviceRow({
  device,
  isThisDevice,
  onRevoke,
  revoking,
}: {
  device: PushSubscriptionItem;
  isThisDevice: boolean;
  onRevoke: (id: string) => void;
  revoking: boolean;
}) {
  const { t } = useTranslation("notification");
  const Icon = device.platform === "WEB" ? Laptop : Smartphone;
  return (
    <li className="flex items-center gap-3 rounded-xl border border-border bg-background-alt/50 p-3">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface text-primary-dark">
        <Icon size={17} aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex flex-wrap items-center gap-2">
          <span className="truncate text-body font-semibold text-text-primary">
            {device.deviceLabel ?? t("push.devices.unnamed")}
          </span>
          {isThisDevice ? (
            <span className="rounded-md bg-chip-bg px-1.5 py-0.5 text-[10px] font-bold text-primary-dark">
              {t("push.devices.thisDevice")}
            </span>
          ) : null}
        </span>
        <span className="block text-caption text-text-secondary">
          {t(`push.platforms.${device.platform}`)}
          {device.lastSeenAt === null
            ? ""
            : ` · ${t("push.devices.lastSeen", { date: new Date(device.lastSeenAt).toLocaleDateString("vi-VN") })}`}
        </span>
      </span>
      <button
        type="button"
        disabled={revoking}
        onClick={() => {
          onRevoke(device.id);
        }}
        aria-label={t("push.devices.revoke")}
        className="flex size-9 shrink-0 items-center justify-center rounded-lg text-text-tertiary hover:bg-surface hover:text-danger disabled:opacity-50"
      >
        <Trash2 size={16} aria-hidden="true" />
      </button>
    </li>
  );
}

/**
 * Khối "Thông báo đẩy" của `/settings/notifications` — G9/G10/G11.
 *
 * Ba điều khiến khối này khó hơn vẻ ngoài:
 *  1. **Không có nút chết.** Push chỉ chạy khi có VAPID key + cấu hình Firebase + trình duyệt
 *     hỗ trợ + (iOS) đã cài PWA. Thiếu cái nào thì ẩn công tắc và nói rõ thiếu cái gì
 *     (`pushAvailability()`), không hiện một công tắc gạt xong không có gì xảy ra.
 *  2. **Nhận ra "máy này".** `GET /push/subscriptions` cố ý không trả `fid` ⇒ id đăng ký của
 *     máy hiện tại được nhớ ở localStorage (xem `pushClient.ts`).
 *  3. **Trần 10 thiết bị** (p12 §12.3.2) trả `409 PUSH_SUBSCRIPTION_LIMIT`; thiếu consent
 *     `HEALTH_REMINDER_PUSH` trả `403 CONSENT_REQUIRED`. Cả hai phải đọc được, không nuốt
 *     thành "lỗi không xác định".
 */
export function PushDevicesCard({ className }: { className?: string }) {
  const { t } = useTranslation("notification");
  // Khả dụng không đổi trong vòng đời trang (env + UA + quyền đã cấp) — chốt một lần.
  const [availability] = useState<PushAvailability>(() => pushAvailability());
  const [localId, setLocalId] = useState<string | null>(() => readLocalSubscriptionId());
  const [busy, setBusy] = useState(false);
  const [clientError, setClientError] = useState<PushRegistrationError["reason"] | null>(null);

  // Khi chưa cấu hình push ở mức sản phẩm thì danh sách thiết bị chắc chắn rỗng — đừng gọi.
  // Nhưng nếu chỉ MÁY NÀY không bật được (iOS chưa cài / trình duyệt cũ / đã chặn quyền) thì
  // các máy khác của user vẫn có thể đang nhận push, và user cần gỡ được chúng từ đây.
  const productConfigured =
    availability.available ||
    (availability.reason !== "MISSING_VAPID_KEY" && availability.reason !== "MISSING_FIREBASE_CONFIG");
  const devices = usePushSubscriptions(productConfigured);
  const upsert = useUpsertPushSubscription();
  const revoke = useRevokePushSubscription();

  const items = devices.data?.items ?? [];
  const enabledHere = localId !== null && items.some((item) => item.id === localId);

  const apiErrorKey = (error: unknown): string => {
    if (isApiError(error) && error.code) return `push.errors.${error.code}`;
    return "push.errors.GENERIC";
  };

  const turnOn = () => {
    setClientError(null);
    setBusy(true);
    void (async () => {
      try {
        const fid = await registerThisDevice();
        const created = await upsert.mutateAsync({
          fid,
          platform: currentPlatform(),
          deviceLabel: currentDeviceLabel(),
        });
        writeLocalSubscriptionId(created.id);
        setLocalId(created.id);
      } catch (error) {
        if (error instanceof PushRegistrationError) setClientError(error.reason);
      } finally {
        setBusy(false);
      }
    })();
  };

  const turnOff = (subscriptionId: string) => {
    setClientError(null);
    setBusy(true);
    void (async () => {
      try {
        await revoke.mutateAsync(subscriptionId);
        if (subscriptionId === localId) {
          await unregisterThisDevice();
          writeLocalSubscriptionId(null);
          setLocalId(null);
        }
      } catch {
        // Lỗi API đã nằm ở `revoke.error`, hiển thị bên dưới.
      } finally {
        setBusy(false);
      }
    })();
  };

  const mutationError = upsert.error ?? revoke.error;

  return (
    <section className={cn("flex flex-col gap-3", className)}>
      <div className="flex items-start gap-3">
        <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-background-alt text-primary-dark">
          <BellRing size={17} aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="text-body font-bold text-text-primary">{t("push.title")}</h2>
          <p className="pt-0.5 text-caption leading-relaxed text-text-secondary">{t("push.lead")}</p>
        </div>
      </div>

      {availability.available ? (
        <div className="flex items-center gap-3 rounded-xl border border-border bg-background-alt/50 p-3">
          <span className="min-w-0 flex-1">
            <span className="block text-body font-semibold text-text-primary">{t("push.toggle.title")}</span>
            <span className="block text-caption text-text-secondary">{t("push.toggle.body")}</span>
          </span>
          {busy ? <Loader2 size={16} className="animate-spin text-text-tertiary" aria-hidden="true" /> : null}
          <Switch
            checked={enabledHere}
            disabled={busy}
            aria-label={t("push.toggle.title")}
            onCheckedChange={(checked) => {
              if (checked) turnOn();
              else if (localId !== null) turnOff(localId);
            }}
          />
        </div>
      ) : (
        <PushBlockedNotice availability={availability} />
      )}

      {clientError !== null ? (
        <p className="flex items-start gap-2 rounded-xl bg-warning-bg px-3.5 py-2.5 text-caption text-warning-text">
          <AlertTriangle size={15} className="mt-0.5 shrink-0" aria-hidden="true" />
          {t(`push.clientErrors.${clientError}`)}
        </p>
      ) : null}

      {mutationError ? (
        <p className="flex items-start gap-2 rounded-xl bg-danger-bg px-3.5 py-2.5 text-caption text-danger-text">
          <AlertTriangle size={15} className="mt-0.5 shrink-0" aria-hidden="true" />
          {t(apiErrorKey(mutationError), { max: 10 })}
        </p>
      ) : null}

      {productConfigured ? (
        <div className="flex flex-col gap-2">
          <p className="text-caption font-semibold uppercase tracking-wide text-text-tertiary">
            {t("push.devices.legend")}
          </p>
          {devices.isPending ? (
            <p className="flex items-center gap-2 text-caption text-text-secondary">
              <Loader2 size={15} className="animate-spin" aria-hidden="true" />
              {t("push.devices.loading")}
            </p>
          ) : devices.isError ? (
            <p className="text-caption text-text-secondary">{t("push.devices.loadFailed")}</p>
          ) : items.length === 0 ? (
            <p className="text-caption text-text-secondary">{t("push.devices.empty")}</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {items.map((device) => (
                <DeviceRow
                  key={device.id}
                  device={device}
                  isThisDevice={device.id === localId}
                  revoking={busy}
                  onRevoke={turnOff}
                />
              ))}
            </ul>
          )}
          <p className="text-caption text-text-tertiary">{t("push.devices.limitNote", { max: 10 })}</p>
        </div>
      ) : null}

      {upsert.isSuccess ? (
        <p className="flex items-center gap-1.5 text-caption text-success-text" aria-live="polite">
          <Check size={14} aria-hidden="true" />
          {t("push.saved")}
        </p>
      ) : null}
    </section>
  );
}
