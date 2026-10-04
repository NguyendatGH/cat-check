import { act, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NotificationUnreadBadge, PushDevicesCard } from "./components";
import { pushAvailability } from "./pushClient";

/**
 * Hai thứ dễ sai nhất ở phần giao diện của nhóm G, và cả hai đều là lỗi "im lặng":
 *
 *  1. **Badge chuông.** Trước gói này header hiện một chấm đỏ CỐ ĐỊNH — luôn sáng kể cả khi
 *     hộp thư trống. Test ép: 0 ⇒ không render gì; n > 0 ⇒ hiện đúng số; > 99 ⇒ "99+".
 *  2. **Nhánh `isPushEnabled = false`.** Thiếu `VITE_FIREBASE_VAPID_KEY` (mặc định của repo,
 *     và cũng là môi trường test) thì KHÔNG được có công tắc bật push — gạt nó sẽ không bao
 *     giờ có token nên là nút chết. Phải thay bằng lời giải thích, và không gọi
 *     `GET /push/subscriptions` (danh sách chắc chắn rỗng).
 *
 * i18n được thay bằng hàm trả thẳng khoá: test này kiểm *cấu trúc hiển thị*, không kiểm câu
 * chữ (câu chữ đã có `claims.test.ts` quét). Dựng bằng `react-dom/client` + `act` vì
 * `@testing-library/dom` không được cài (xem `hooks.test.tsx`).
 */
vi.mock("react-i18next", async (importOriginal) => ({
  // Mock TỪNG PHẦN: `shared/api/middleware/locale.ts` kéo theo `shared/i18n/i18n.ts`, file đó
  // cần `initReactI18next` thật lúc nạp module. Mock trọn gói làm cả suite không khởi động được.
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ t: (key: string) => key }),
}));

const fetchMock = vi.fn();

function newClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
}

interface Mounted {
  container: HTMLElement;
  unmount: () => void;
}

async function render(node: ReactNode, client: QueryClient): Promise<Mounted> {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  await act(async () => {
    root.render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
    await Promise.resolve();
  });
  return {
    container,
    unmount: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
    },
  };
}

/** Chờ `predicate` thành true, mỗi vòng bọc trong `act` để React xả hết cập nhật. */
async function waitUntil(predicate: () => boolean, label: string): Promise<void> {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    if (predicate()) return;
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 5));
    });
  }
  throw new Error(`Hết thời gian chờ: ${label}`);
}

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function badge(container: HTMLElement): HTMLElement | null {
  return container.querySelector<HTMLElement>('[data-testid="notification-unread-badge"]');
}

describe("NotificationUnreadBadge — G5", () => {
  it("không render gì khi không còn thông báo chưa đọc", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ unreadCount: 0 }), { headers: { "Content-Type": "application/json" } }),
    );

    const view = await render(<NotificationUnreadBadge />, newClient());
    await waitUntil(() => fetchMock.mock.calls.length > 0, "gọi unread-count");
    await act(async () => {
      await Promise.resolve();
    });

    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/v1/notifications/unread-count");
    expect(badge(view.container)).toBeNull();
    view.unmount();
  });

  it("hiện đúng số khi có thông báo chưa đọc", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ unreadCount: 3 }), { headers: { "Content-Type": "application/json" } }),
    );

    const view = await render(<NotificationUnreadBadge />, newClient());
    await waitUntil(() => badge(view.container) !== null, "badge xuất hiện");

    expect(badge(view.container)?.textContent).toBe("3");
    view.unmount();
  });

  it("kẹp ở 99+ để bong bóng không phá bố cục header", async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ unreadCount: 1240 }), { headers: { "Content-Type": "application/json" } }),
    );

    const view = await render(<NotificationUnreadBadge />, newClient());
    await waitUntil(() => badge(view.container) !== null, "badge xuất hiện");

    expect(badge(view.container)?.textContent).toBe("99+");
    view.unmount();
  });

  it("khách chưa đăng nhập: không gọi endpoint, không badge", async () => {
    const view = await render(<NotificationUnreadBadge enabled={false} />, newClient());
    expect(fetchMock).not.toHaveBeenCalled();
    expect(badge(view.container)).toBeNull();
    view.unmount();
  });
});

describe("PushDevicesCard — nhánh isPushEnabled = false", () => {
  it("môi trường test không có VAPID key ⇒ pushAvailability() = MISSING_VAPID_KEY", () => {
    const availability = pushAvailability();
    expect(availability.available).toBe(false);
    expect(availability.available ? null : availability.reason).toBe("MISSING_VAPID_KEY");
  });

  it("không render công tắc, chỉ render lời giải thích — và KHÔNG gọi GET /push/subscriptions", async () => {
    const view = await render(<PushDevicesCard />, newClient());
    await act(async () => {
      await Promise.resolve();
    });

    expect(view.container.querySelector('[role="switch"]')).toBeNull();
    expect(view.container.textContent).toContain("push.blocked.MISSING_VAPID_KEY.title");
    expect(view.container.textContent).toContain("push.blocked.MISSING_VAPID_KEY.body");
    // Danh sách thiết bị cũng không được gọi: chưa cấu hình ở mức sản phẩm thì chắc chắn rỗng.
    expect(fetchMock).not.toHaveBeenCalled();
    expect(view.container.textContent).not.toContain("push.devices.legend");
    view.unmount();
  });
});
