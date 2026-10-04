import { firebaseVapidKey, firebaseWebConfig, isPushEnabled } from "@/shared/config/env";
import type { PushPlatform } from "./types";

/**
 * Lớp trình duyệt của web push (p12 §12.3). KHÔNG gọi API CatCheck ở đây — file này chỉ trả
 * lời hai câu: "máy này có đăng ký push được không?" và "định danh thiết bị là gì?".
 *
 * Vì sao tách khỏi `hooks.ts`: toàn bộ phần dưới phụ thuộc vào `window`/`navigator`/Firebase
 * SDK, còn hooks thì chỉ cần `fetch`. Tách ra để test hook không phải giả lập cả trình duyệt,
 * và để `firebase/*` nằm trong đúng một file (rule eslint chỉ cho import nó ở
 * `features/notification` + `src/sw.ts`).
 */

/** Vì sao máy này KHÔNG bật push được. Mỗi mã là một câu giải thích khác nhau cho user. */
export type PushBlockReason =
  /** Chưa có `VITE_FIREBASE_VAPID_KEY` — Y4: push tắt tới khi có key thật. */
  | "MISSING_VAPID_KEY"
  /** Có VAPID key nhưng thiếu cấu hình Firebase Web App ⇒ `initializeApp` không chạy được. */
  | "MISSING_FIREBASE_CONFIG"
  /** iOS/iPadOS: push chỉ hoạt động sau khi "Thêm vào Màn hình chính" (p12 §12.3.8). */
  | "IOS_NEEDS_INSTALL"
  /** Trình duyệt không có Notification/ServiceWorker/PushManager. */
  | "UNSUPPORTED_BROWSER"
  /** User đã từ chối quyền — p12 §12.3.5 cấm hỏi lại bằng API trình duyệt. */
  | "PERMISSION_DENIED";

export type PushAvailability = { available: true } | { available: false; reason: PushBlockReason };

function isStandaloneDisplay(): boolean {
  // `navigator.standalone` chỉ có trên Safari iOS; `display-mode: standalone` là đường chuẩn.
  const legacyStandalone = (navigator as Navigator & { standalone?: boolean }).standalone === true;
  const mediaStandalone =
    typeof window.matchMedia === "function" && window.matchMedia("(display-mode: standalone)").matches;
  return legacyStandalone || mediaStandalone;
}

function isAppleMobile(): boolean {
  const ua = navigator.userAgent;
  // iPadOS ≥ 13 khai UA là "Macintosh"; phân biệt bằng số điểm chạm.
  const iPadOs = ua.includes("Macintosh") && navigator.maxTouchPoints > 1;
  return /iPhone|iPad|iPod/.test(ua) || iPadOs;
}

/**
 * Trạng thái ĐỒNG BỘ, đủ để quyết định render nút hay render lời giải thích.
 *
 * Thứ tự kiểm là có chủ đích: trên iOS Safari chưa cài PWA thì `Notification` KHÔNG tồn tại
 * trong DOM (p12 §12.3.8), nên nếu kiểm "trình duyệt hỗ trợ" trước thì user iPhone sẽ nhận
 * câu "trình duyệt không hỗ trợ" — sai và không hành động được. Kiểm iOS trước để đưa đúng
 * hướng dẫn cài PWA.
 */
export function pushAvailability(): PushAvailability {
  if (!isPushEnabled || !firebaseVapidKey) return { available: false, reason: "MISSING_VAPID_KEY" };
  if (!firebaseWebConfig) return { available: false, reason: "MISSING_FIREBASE_CONFIG" };
  if (isAppleMobile() && !isStandaloneDisplay()) return { available: false, reason: "IOS_NEEDS_INSTALL" };
  if (!("Notification" in window) || !("serviceWorker" in navigator) || !("PushManager" in window)) {
    return { available: false, reason: "UNSUPPORTED_BROWSER" };
  }
  if (Notification.permission === "denied") return { available: false, reason: "PERMISSION_DENIED" };
  return { available: true };
}

/** `platform` của p4 F3 suy ra từ UA + chế độ hiển thị. */
export function currentPlatform(): PushPlatform {
  const standalone = isStandaloneDisplay();
  if (!standalone) return "WEB";
  if (isAppleMobile()) return "IOS_PWA";
  return navigator.userAgent.includes("Android") ? "ANDROID_PWA" : "WEB";
}

/**
 * Nhãn thiết bị cho user tự nhận ra máy mình — `device_label` của p4 F3, tối đa 100 ký tự.
 *
 * CỐ Ý không gửi `navigator.userAgent` đầy đủ làm nhãn: cột này hiện nguyên văn trong UI và
 * UA chuỗi đầy đủ là dấu vân tay thiết bị. Backend đã tự lấy UA từ header nếu cần.
 */
export function currentDeviceLabel(): string {
  const ua = navigator.userAgent;
  const browser = /Edg\//.test(ua)
    ? "Edge"
    : /OPR\//.test(ua)
      ? "Opera"
      : /Firefox\//.test(ua)
        ? "Firefox"
        : /Chrome\//.test(ua)
          ? "Chrome"
          : /Safari\//.test(ua)
            ? "Safari"
            : "Trình duyệt";
  const os = /Android/.test(ua)
    ? "Android"
    : isAppleMobile()
      ? "iOS"
      : /Windows/.test(ua)
        ? "Windows"
        : /Mac OS X/.test(ua)
          ? "macOS"
          : /Linux/.test(ua)
            ? "Linux"
            : "";
  return (os ? `${browser} · ${os}` : browser).slice(0, 100);
}

/** Lý do không lấy được FID — phân biệt với lỗi API để UI nói đúng chuyện. */
export class PushRegistrationError extends Error {
  readonly reason: "PERMISSION_DENIED" | "PERMISSION_DISMISSED" | "SDK_UNSUPPORTED" | "TIMEOUT";

  constructor(reason: PushRegistrationError["reason"]) {
    super(`Push registration failed: ${reason}`);
    this.name = "PushRegistrationError";
    this.reason = reason;
  }
}

/** Quá hạn chờ FCM cấp FID. Trên mạng kém, `register()` có thể treo im lặng. */
const FID_TIMEOUT_MS = 20_000;

/**
 * Xin quyền (nếu cần) rồi lấy FID từ FCM — p12 §12.3.2.
 *
 * Dùng `register()` + `onRegistered()` của SDK 12.x, **không** `getToken()` (đã deprecated,
 * p12 §12.3.2 cấm). Import động để firebase (~200KB) không vào bundle chính: màn duy nhất
 * cần nó là `/settings/notifications`.
 *
 * Service worker lấy từ `navigator.serviceWorker.ready` chứ không tự đăng ký file riêng —
 * p12 §12.3.3: một scope chỉ có MỘT service worker, và `vite-plugin-pwa` đã đăng ký `sw.ts`.
 */
export async function registerThisDevice(): Promise<string> {
  if (!firebaseWebConfig || !firebaseVapidKey) throw new PushRegistrationError("SDK_UNSUPPORTED");
  // Gán vào biến cục bộ: TS không giữ được narrowing của binding import bên trong closure dưới.
  const vapidKey = firebaseVapidKey;

  if (Notification.permission === "default") {
    const result = await Notification.requestPermission();
    if (result === "denied") throw new PushRegistrationError("PERMISSION_DENIED");
    if (result !== "granted") throw new PushRegistrationError("PERMISSION_DISMISSED");
  } else if (Notification.permission !== "granted") {
    throw new PushRegistrationError("PERMISSION_DENIED");
  }

  const [{ getApps, initializeApp }, messagingModule] = await Promise.all([
    import("firebase/app"),
    import("firebase/messaging"),
  ]);
  const { getMessaging, isSupported, onRegistered, register } = messagingModule;

  if (!(await isSupported())) throw new PushRegistrationError("SDK_UNSUPPORTED");

  const app = getApps().at(0) ?? initializeApp(firebaseWebConfig);
  const messaging = getMessaging(app);
  const serviceWorkerRegistration = await navigator.serviceWorker.ready;

  return await new Promise<string>((resolve, reject) => {
    let settled = false;
    const timer = setTimeout(() => {
      if (settled) return;
      settled = true;
      unsubscribe();
      reject(new PushRegistrationError("TIMEOUT"));
    }, FID_TIMEOUT_MS);

    const unsubscribe = onRegistered(messaging, (fid: string) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      unsubscribe();
      resolve(fid);
    });

    register(messaging, { vapidKey, serviceWorkerRegistration }).catch((error: unknown) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      unsubscribe();
      reject(error instanceof Error ? error : new PushRegistrationError("SDK_UNSUPPORTED"));
    });
  });
}

/**
 * Bảo FCM ngừng cấp cho máy này (p12 §12.3.9: `onUnregistered` ⇒ báo backend revoke ngay).
 *
 * Gọi SAU khi `DELETE /push/subscriptions/{id}` thành công. Lỗi ở đây KHÔNG được làm hỏng
 * thao tác tắt: bản ghi server đã bị thu hồi rồi, cùng lắm là SDK còn giữ FID chết mà
 * `CleanupDeadPushTokensJob` sẽ dọn.
 */
export async function unregisterThisDevice(): Promise<void> {
  if (!firebaseWebConfig) return;
  try {
    const [{ getApps }, { getMessaging, isSupported, unregister }] = await Promise.all([
      import("firebase/app"),
      import("firebase/messaging"),
    ]);
    const app = getApps().at(0);
    if (!app || !(await isSupported())) return;
    await unregister(getMessaging(app));
  } catch {
    // Im lặng có chủ đích — xem Javadoc trên.
  }
}

/**
 * Khoá localStorage ghi id đăng ký của CHÍNH máy này.
 *
 * Vì sao cần: `GET /push/subscriptions` cố ý KHÔNG trả `fid`/`legacyToken` (cột PII,
 * p12 §12.6.4a) nên từ danh sách thuần không có cách nào biết dòng nào là máy đang ngồi.
 * Không có mẩu state này thì công tắc "bật push trên máy này" sẽ phải đoán — hoặc tệ hơn,
 * hiện sai trạng thái. localStorage là đúng phạm vi: dữ liệu CỦA thiết bị, chết cùng thiết bị.
 */
const LOCAL_SUBSCRIPTION_KEY = "catcheck.push.subscriptionId";

export function readLocalSubscriptionId(): string | null {
  try {
    return localStorage.getItem(LOCAL_SUBSCRIPTION_KEY);
  } catch {
    // Safari chế độ riêng tư / storage bị chặn — coi như chưa đăng ký.
    return null;
  }
}

export function writeLocalSubscriptionId(subscriptionId: string | null): void {
  try {
    if (subscriptionId === null) localStorage.removeItem(LOCAL_SUBSCRIPTION_KEY);
    else localStorage.setItem(LOCAL_SUBSCRIPTION_KEY, subscriptionId);
  } catch {
    // Không ghi được thì công tắc chỉ mất trí nhớ sau khi tải lại trang — không chặn luồng.
  }
}
