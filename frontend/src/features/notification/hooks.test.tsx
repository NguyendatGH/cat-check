import { act, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  useHideNotification,
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotificationInbox,
  usePushSubscriptions,
  useRevokePushSubscription,
  useUnreadNotificationCount,
  useUpsertPushSubscription,
} from "./hooks";
import type { NotificationItem, NotificationListResponse } from "./types";

/**
 * Nhóm G của p8 §8.4.7. Test khoá vào đúng những chỗ gãy khi nối API thật:
 *  1. **Cursor**: trang 2 phải mang `cursor` của trang 1 và chỉ đi tiếp khi `page.hasMore`
 *     — đọc nhầm `nextCursor` (có thể khác null ở trang cuối) là lặp vô hạn.
 *  2. **Invalidate sau khi đánh dấu đã đọc**: badge chuông là query RIÊNG
 *     (`/notifications/unread-count`), không tự giảm theo danh sách. Invalidate phải phủ gốc
 *     `['notification']`, nếu không số trên chuông đứng im sau khi user đã đọc.
 *  3. Đúng method/URL cho 5 endpoint còn lại (một ký tự sai là 404/405 lúc chạy thật).
 *
 * Dựng hook bằng `react-dom/client` + `act` thay vì `@testing-library/react`: package đó có
 * trong devDependencies nhưng peer `@testing-library/dom` KHÔNG được cài, và brief cấm thêm
 * gói mới (giống `features/insight/hooks.test.tsx`).
 */

const ITEM: NotificationItem = {
  id: "0199cc00-0000-7000-8000-000000000001",
  templateCode: "REMINDER_SCAN_DUE",
  title: "Đến lịch quét cho Mun",
  body: "Lịch theo dõi bạn đặt cho Mun đã tới hạn hôm nay.",
  deepLink: "/reminders",
  refType: "REMINDER",
  refId: "0199cc00-0000-7000-8000-000000000002",
  read: false,
  readAt: null,
  createdAt: "2026-10-01T02:00:00Z",
};

function page(items: NotificationItem[], nextCursor: string | null, hasMore: boolean): NotificationListResponse {
  return { items, page: { limit: 20, nextCursor, hasMore } };
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function newClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
}

interface HookHandle<T> {
  readonly current: T;
  unmount: () => void;
}

/** `renderHook` tối giản — đủ cho việc đọc giá trị hook qua nhiều lần render lại. */
async function renderHook<T>(useHookFn: () => T, client: QueryClient): Promise<HookHandle<T>> {
  const box: { current: T | undefined } = { current: undefined };
  function Probe() {
    box.current = useHookFn();
    return null;
  }
  function Tree({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  }
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  await act(async () => {
    root.render(
      <Tree>
        <Probe />
      </Tree>,
    );
    await Promise.resolve();
  });
  return {
    get current() {
      return box.current as T;
    },
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

const fetchMock = vi.fn();

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("useNotificationInbox — G4 (phân trang cursor)", () => {
  it("trang đầu gọi GET /notifications?limit=20 và KHÔNG kèm cursor", async () => {
    fetchMock.mockResolvedValue(jsonResponse(page([ITEM], null, false)));

    const hook = await renderHook(() => useNotificationInbox(), newClient());
    await waitUntil(() => hook.current.isSuccess, "trang đầu về");

    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url.startsWith("/api/v1/notifications?")).toBe(true);
    const params = new URLSearchParams(url.split("?")[1]);
    expect(params.get("limit")).toBe("20");
    expect(params.has("cursor")).toBe(false);
    expect(hook.current.data?.pages[0]?.items).toHaveLength(1);
    hook.unmount();
  });

  it("fetchNextPage gửi đúng cursor của trang trước rồi dừng khi hasMore = false", async () => {
    const second: NotificationItem = { ...ITEM, id: "0199cc00-0000-7000-8000-00000000000b", read: true };
    fetchMock
      .mockResolvedValueOnce(jsonResponse(page([ITEM], "cursor-trang-2", true)))
      .mockResolvedValueOnce(jsonResponse(page([second], "cursor-khong-dung", false)));

    const hook = await renderHook(() => useNotificationInbox(), newClient());
    await waitUntil(() => hook.current.isSuccess, "trang đầu về");
    expect(hook.current.hasNextPage).toBe(true);

    await act(async () => {
      await hook.current.fetchNextPage();
    });
    await waitUntil(() => (hook.current.data?.pages.length ?? 0) === 2, "trang hai về");

    const secondUrl = String(fetchMock.mock.calls[1]?.[0]);
    expect(new URLSearchParams(secondUrl.split("?")[1]).get("cursor")).toBe("cursor-trang-2");
    // `nextCursor` của trang 2 KHÁC null nhưng `hasMore = false` ⇒ phải dừng, không lặp.
    expect(hook.current.hasNextPage).toBe(false);
    hook.unmount();
  });

  it("không gọi gì khi bị tắt (khách chưa đăng nhập)", async () => {
    const hook = await renderHook(() => useNotificationInbox(false), newClient());
    expect(fetchMock).not.toHaveBeenCalled();
    expect(hook.current.fetchStatus).toBe("idle");
    hook.unmount();
  });
});

describe("useUnreadNotificationCount — G5", () => {
  it("gọi GET /notifications/unread-count và trả số", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ unreadCount: 7 }));

    const hook = await renderHook(() => useUnreadNotificationCount(), newClient());
    await waitUntil(() => hook.current.isSuccess, "đếm xong");

    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/v1/notifications/unread-count");
    expect(hook.current.data?.unreadCount).toBe(7);
    hook.unmount();
  });

  it("không gọi khi chưa đăng nhập", async () => {
    const hook = await renderHook(() => useUnreadNotificationCount(false), newClient());
    expect(fetchMock).not.toHaveBeenCalled();
    hook.unmount();
  });
});

describe("mutation hộp thư — G6/G7/G8", () => {
  it("markRead POST đúng URL, chịu 204 rỗng và invalidate gốc ['notification']", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const client = newClient();
    const invalidate = vi.spyOn(client, "invalidateQueries");

    const hook = await renderHook(() => useMarkNotificationRead(), client);
    act(() => {
      hook.current.mutate(ITEM.id);
    });
    await waitUntil(() => hook.current.isSuccess, "markRead xong");

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`/api/v1/notifications/${ITEM.id}/read`);
    expect(init.method).toBe("POST");
    // Gốc `['notification']` phủ CẢ `inbox` LẪN `unread-count` — badge chuông phải giảm theo.
    const keys = invalidate.mock.calls.map((call) => JSON.stringify(call[0]?.queryKey));
    expect(keys).toContain(JSON.stringify(["notification"]));
    hook.unmount();
  });

  it("read-all POST /notifications/read-all và invalidate gốc", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const client = newClient();
    const invalidate = vi.spyOn(client, "invalidateQueries");

    const hook = await renderHook(() => useMarkAllNotificationsRead(), client);
    act(() => {
      hook.current.mutate();
    });
    await waitUntil(() => hook.current.isSuccess, "read-all xong");

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/notifications/read-all");
    expect(init.method).toBe("POST");
    expect(invalidate.mock.calls.map((call) => JSON.stringify(call[0]?.queryKey))).toContain(
      JSON.stringify(["notification"]),
    );
    hook.unmount();
  });

  it("hide DELETE /notifications/{id}", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    const hook = await renderHook(() => useHideNotification(), newClient());
    act(() => {
      hook.current.mutate(ITEM.id);
    });
    await waitUntil(() => hook.current.isSuccess, "hide xong");

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`/api/v1/notifications/${ITEM.id}`);
    expect(init.method).toBe("DELETE");
    hook.unmount();
  });
});

describe("thiết bị push — G9/G10/G11", () => {
  it("liệt kê qua GET /push/subscriptions", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ items: [], page: { limit: 0, nextCursor: null, hasMore: false } }));

    const hook = await renderHook(() => usePushSubscriptions(), newClient());
    await waitUntil(() => hook.current.isSuccess, "danh sách thiết bị về");

    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/v1/push/subscriptions");
    hook.unmount();
  });

  it("upsert PUT /push/subscriptions với body fid/platform/deviceLabel", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        id: "0199dd00-0000-7000-8000-000000000003",
        platform: "WEB",
        deviceLabel: "Chrome · Linux",
        lastSeenAt: null,
        lastSuccessAt: null,
        createdAt: "2026-10-03T01:00:00Z",
      }),
    );

    const hook = await renderHook(() => useUpsertPushSubscription(), newClient());
    act(() => {
      hook.current.mutate({ fid: "fid-abc", platform: "WEB", deviceLabel: "Chrome · Linux" });
    });
    await waitUntil(() => hook.current.isSuccess, "upsert xong");

    // `body` khai kiểu hẹp hơn `RequestInit` ở đây để đọc được JSON đã gửi mà không phải
    // stringify một `BodyInit` (eslint `no-base-to-string`).
    const [url, init] = fetchMock.mock.calls[0] as [string, { method: string; body: string }];
    expect(url).toBe("/api/v1/push/subscriptions");
    expect(init.method).toBe("PUT");
    expect(JSON.parse(init.body)).toEqual({ fid: "fid-abc", platform: "WEB", deviceLabel: "Chrome · Linux" });
    hook.unmount();
  });

  it("giữ nguyên mã lỗi 409 PUSH_SUBSCRIPTION_LIMIT để UI hiện được trần 10 thiết bị", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ detail: "Vuot tran thiet bi", errorCode: "PUSH_SUBSCRIPTION_LIMIT" }, 409),
    );

    const hook = await renderHook(() => useUpsertPushSubscription(), newClient());
    act(() => {
      hook.current.mutate({ fid: "fid-abc" });
    });
    await waitUntil(() => hook.current.isError, "upsert lỗi");

    const error = hook.current.error as { status?: number; code?: string } | null;
    expect(error?.status).toBe(409);
    expect(error?.code).toBe("PUSH_SUBSCRIPTION_LIMIT");
    hook.unmount();
  });

  it("revoke DELETE /push/subscriptions/{id}", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    const hook = await renderHook(() => useRevokePushSubscription(), newClient());
    act(() => {
      hook.current.mutate("0199dd00-0000-7000-8000-000000000003");
    });
    await waitUntil(() => hook.current.isSuccess, "revoke xong");

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/v1/push/subscriptions/0199dd00-0000-7000-8000-000000000003");
    expect(init.method).toBe("DELETE");
    hook.unmount();
  });
});
