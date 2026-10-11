import { act } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { usePushSubscriptionRefresh } from "./pushRefresh";

/**
 * Backend xoá đăng ký push sau 180 ngày không làm mới; `last_seen_at` chỉ đổi ở
 * `PUT /push/subscriptions`. Test khoá 4 điều kiện của việc làm mới lúc mở app: có gọi PUT khi
 * đủ điều kiện, và KHÔNG gọi khi chưa đăng nhập / máy chưa từng bật push / chưa cấp quyền.
 * Dựng bằng `react-dom/client` + `act` (xem `hooks.test.tsx`: `@testing-library/dom` không cài).
 */

const client = vi.hoisted((): { available: boolean; localId: string | null; written: Array<string | null> } => ({
  available: true,
  localId: "sub-1",
  written: [],
}));

vi.mock("./pushClient", () => ({
  currentDeviceLabel: () => "Chrome · Linux",
  currentPlatform: () => "WEB",
  pushAvailability: () => (client.available ? { available: true } : { available: false, reason: "PERMISSION_DENIED" }),
  readLocalSubscriptionId: () => client.localId,
  registerThisDevice: () => Promise.resolve("fid-abc"),
  writeLocalSubscriptionId: (id: string | null) => {
    client.written.push(id);
  },
}));

const fetchMock = vi.fn();

function Probe({ signedIn }: { signedIn: boolean }) {
  usePushSubscriptionRefresh(signedIn);
  return null;
}

async function mount(signedIn: boolean): Promise<() => void> {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <Probe signedIn={signedIn} />
      </QueryClientProvider>,
    );
    await new Promise((resolve) => setTimeout(resolve, 20));
  });
  return () => {
    act(() => {
      root.unmount();
    });
    container.remove();
  };
}

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  client.available = true;
  client.localId = "sub-1";
  client.written = [];
  fetchMock.mockReset();
  fetchMock.mockResolvedValue(
    new Response(JSON.stringify({ id: "sub-1", platform: "WEB" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  );
  vi.stubGlobal("fetch", fetchMock);
  vi.stubGlobal("Notification", { permission: "granted" });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("usePushSubscriptionRefresh", () => {
  it("đã đăng nhập + máy từng bật push + quyền granted ⇒ PUT /push/subscriptions với fid hiện tại", async () => {
    const unmount = await mount(true);
    const put = fetchMock.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === "PUT");
    expect(put?.[0]).toBe("/api/v1/push/subscriptions");
    const body = (put?.[1] as RequestInit).body;
    expect(typeof body).toBe("string");
    expect(JSON.parse(body as string)).toEqual({
      fid: "fid-abc",
      platform: "WEB",
      deviceLabel: "Chrome · Linux",
    });
    expect(client.written).toEqual([]);
    unmount();
  });

  it("server trả id khác id cục bộ ⇒ cập nhật id cục bộ để công tắc vẫn khớp", async () => {
    client.localId = "old-id";
    const unmount = await mount(true);
    expect(client.written).toEqual(["sub-1"]);
    unmount();
  });

  it.each([
    ["chưa đăng nhập", () => mount(false)],
    [
      "máy chưa từng bật push (không có id cục bộ)",
      () => {
        client.localId = null;
        return mount(true);
      },
    ],
    [
      "push không khả dụng (quyền bị từ chối)",
      () => {
        client.available = false;
        return mount(true);
      },
    ],
    [
      "quyền còn ở 'default' — không được hiện hộp thoại xin quyền lúc mở app",
      () => {
        vi.stubGlobal("Notification", { permission: "default" });
        return mount(true);
      },
    ],
  ])("không gọi API khi %s", async (_label, run) => {
    const unmount = await run();
    expect(fetchMock).not.toHaveBeenCalled();
    unmount();
  });
});
