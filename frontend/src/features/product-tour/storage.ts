import { useSyncExternalStore } from "react";

/**
 * Trạng thái hướng dẫn tính năng (product tour) theo từng tài khoản.
 * - pending: sẽ tự hiện khi vào Trang chủ.
 * - seen: đã xem / đã đóng.
 * - disabled: người dùng chọn "Không hiển thị lại".
 *
 * Lưu localStorage theo user id (không có cột tuỳ chọn UI phía backend; notification_prefs chỉ dành
 * cho thông báo). Mọi truy cập storage bọc try/catch — trình duyệt chặn storage thì coi như "seen"
 * để không làm phiền lặp lại.
 */
export type TourStatus = "pending" | "seen" | "disabled";

const KEY_PREFIX = "catcheck.productTour.v1:";
const STATUSES: readonly TourStatus[] = ["pending", "seen", "disabled"];

const listeners = new Set<() => void>();

function storageKey(userId: string): string {
  return `${KEY_PREFIX}${userId}`;
}

function isTourStatus(value: unknown): value is TourStatus {
  return typeof value === "string" && (STATUSES as readonly string[]).includes(value);
}

export function readTourStatus(userId: string | null | undefined): TourStatus {
  if (!userId) return "seen";
  try {
    const raw = window.localStorage.getItem(storageKey(userId));
    if (raw === null) return "pending";
    return isTourStatus(raw) ? raw : "pending";
  } catch {
    return "seen";
  }
}

export function writeTourStatus(userId: string | null | undefined, status: TourStatus): void {
  if (!userId) return;
  try {
    window.localStorage.setItem(storageKey(userId), status);
  } catch {
    // Storage bị chặn (private mode / quota): bỏ qua, chỉ ảnh hưởng việc ghi nhớ.
  }
  for (const listener of listeners) listener();
}

function subscribe(onChange: () => void): () => void {
  listeners.add(onChange);
  const onStorage = (event: StorageEvent) => {
    if (event.key === null || event.key.startsWith(KEY_PREFIX)) onChange();
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(onChange);
    window.removeEventListener("storage", onStorage);
  };
}

export function useTourStatus(userId: string | null | undefined): TourStatus {
  return useSyncExternalStore(
    subscribe,
    () => readTourStatus(userId),
    (): TourStatus => "seen",
  );
}
