/// <reference lib="webworker" />

import { precacheAndRoute, cleanupOutdatedCaches } from "workbox-precaching";
import { registerRoute, NavigationRoute } from "workbox-routing";
import { createHandlerBoundToURL } from "workbox-precaching";

declare const self: ServiceWorkerGlobalScope;

precacheAndRoute(self.__WB_MANIFEST);
cleanupOutdatedCaches();

registerRoute(new NavigationRoute(createHandlerBoundToURL("/index.html")));

self.addEventListener("install", () => {
  void self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

// Backend gửi FCM data-only (title, body, url, notificationId), nên service worker phải tự hiện thông báo.
self.addEventListener("push", (event) => {
  let payload: { data?: Record<string, string> } & Record<string, unknown> = {};
  try {
    payload = event.data?.json() ?? {};
  } catch {
    // Payload không phải JSON: vẫn phải hiện một thông báo (userVisibleOnly).
  }
  const data = payload.data ?? (payload as Record<string, string>);
  event.waitUntil(
    self.registration.showNotification(data.title ?? "CatCheck", {
      body: data.body,
      icon: "/icons/pwa-192x192.png",
      tag: data.notificationId,
      data: { url: data.url },
    }),
  );
});

// Chỉ mở link cùng origin: url đến từ payload push.
function sameOriginUrl(raw: string | undefined): string {
  try {
    const target = new URL(raw ?? "/", self.location.origin);
    if (target.origin === self.location.origin) return target.href;
  } catch {
    // URL hỏng: về trang chủ.
  }
  return self.location.origin + "/";
}

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = sameOriginUrl((event.notification.data as { url?: string } | undefined)?.url);
  event.waitUntil(
    self.clients.matchAll({ type: "window" }).then((windows) => {
      const open = windows.at(0);
      return open ? open.focus().then((w) => w.navigate(url)) : self.clients.openWindow(url);
    }),
  );
});

