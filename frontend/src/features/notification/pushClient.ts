import { firebaseVapidKey, firebaseWebConfig, isPushEnabled } from "@/shared/config/env";
import type { PushPlatform } from "./types";


export type PushBlockReason =
  | "MISSING_VAPID_KEY"
  | "MISSING_FIREBASE_CONFIG"
  | "IOS_NEEDS_INSTALL"
  | "UNSUPPORTED_BROWSER"
  | "PERMISSION_DENIED";

export type PushAvailability = { available: true } | { available: false; reason: PushBlockReason };

function isStandaloneDisplay(): boolean {
  const legacyStandalone = (navigator as Navigator & { standalone?: boolean }).standalone === true;
  const mediaStandalone =
    typeof window.matchMedia === "function" && window.matchMedia("(display-mode: standalone)").matches;
  return legacyStandalone || mediaStandalone;
}

function isAppleMobile(): boolean {
  const ua = navigator.userAgent;
  const iPadOs = ua.includes("Macintosh") && navigator.maxTouchPoints > 1;
  return /iPhone|iPad|iPod/.test(ua) || iPadOs;
}

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

export function currentPlatform(): PushPlatform {
  const standalone = isStandaloneDisplay();
  if (!standalone) return "WEB";
  if (isAppleMobile()) return "IOS_PWA";
  return navigator.userAgent.includes("Android") ? "ANDROID_PWA" : "WEB";
}

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

export class PushRegistrationError extends Error {
  readonly reason: "PERMISSION_DENIED" | "PERMISSION_DISMISSED" | "SDK_UNSUPPORTED" | "TIMEOUT";

  constructor(reason: PushRegistrationError["reason"]) {
    super(`Push registration failed: ${reason}`);
    this.name = "PushRegistrationError";
    this.reason = reason;
  }
}

const FID_TIMEOUT_MS = 20_000;

export async function registerThisDevice(): Promise<string> {
  if (!firebaseWebConfig || !firebaseVapidKey) throw new PushRegistrationError("SDK_UNSUPPORTED");
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
  }
}

const LOCAL_SUBSCRIPTION_KEY = "catcheck.push.subscriptionId";

export function readLocalSubscriptionId(): string | null {
  try {
    return localStorage.getItem(LOCAL_SUBSCRIPTION_KEY);
  } catch {
    return null;
  }
}

export function writeLocalSubscriptionId(subscriptionId: string | null): void {
  try {
    if (subscriptionId === null) localStorage.removeItem(LOCAL_SUBSCRIPTION_KEY);
    else localStorage.setItem(LOCAL_SUBSCRIPTION_KEY, subscriptionId);
  } catch {
  }
}
