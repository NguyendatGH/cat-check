/// <reference lib="webworker" />

import { precacheAndRoute, cleanupOutdatedCaches } from "workbox-precaching";
import { registerRoute, NavigationRoute } from "workbox-routing";
import { createHandlerBoundToURL } from "workbox-precaching";

declare const self: ServiceWorkerGlobalScope;

// injectManifest strategy (vite-plugin-pwa) — self.__WB_MANIFEST được thay bằng danh sách
// file thật lúc build.
precacheAndRoute(self.__WB_MANIFEST);
cleanupOutdatedCaches();

// SPA fallback cho navigation request khi offline và trang chưa được precache — trỏ về
// app shell (index.html), router phía client sẽ tự render /offline nếu cần.
registerRoute(new NavigationRoute(createHandlerBoundToURL("/index.html")));

self.addEventListener("install", () => {
  void self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

/**
 * TODO — FCM background message handler: CHƯA có Firebase project + VAPID key thật (Y4
 * mặc định "push tắt" tới khi có VAPID key — xem shared/config/env.ts `isPushEnabled`).
 * Khi có key thật, khởi tạo firebase/app + firebase/messaging/sw ở đây và xử lý
 * `onBackgroundMessage` để hiện notification hệ thống. Import `firebase/*` CHỈ được phép
 * ở src/sw.ts và features/notification (rule ESLint no-restricted-imports).
 *
 * import { initializeApp } from "firebase/app";
 * import { getMessaging, onBackgroundMessage } from "firebase/messaging/sw";
 * const app = initializeApp({ ... });
 * onBackgroundMessage(getMessaging(app), (payload) => { ... });
 */
