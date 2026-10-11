import { act } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PushDevicesCard } from "./components";

/**
 * Danh sách thiết bị nhận push. Backend `default-property-inclusion: non_null` nên một đăng ký
 * chưa có `lastSeenAt` KHÔNG có key đó trong JSON — hàng thiết bị từng in "Invalid Date".
 * `pushAvailability` giả "bị từ chối quyền" để `productConfigured` bật (cần gọi GET danh sách)
 * mà không dựng công tắc.
 */
vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
  }),
}));
vi.mock("./pushClient", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./pushClient")>()),
  pushAvailability: () => ({ available: false, reason: "PERMISSION_DENIED" }),
  readLocalSubscriptionId: () => null,
}));

const fetchMock = vi.fn();

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

async function renderCard(items: unknown[]): Promise<{ text: () => string; unmount: () => void }> {
  fetchMock.mockResolvedValue(
    new Response(JSON.stringify({ items, page: { limit: 10, nextCursor: null, hasMore: false } }), {
      headers: { "Content-Type": "application/json" },
    }),
  );
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <PushDevicesCard />
      </QueryClientProvider>,
    );
    await Promise.resolve();
  });
  // Chờ danh sách tải xong (mỗi vòng bọc trong `act` để React xả cập nhật).
  for (let attempt = 0; attempt < 50 && container.textContent.includes("push.devices.loading"); attempt += 1) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 5));
    });
  }
  return {
    text: () => container.textContent,
    unmount: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
    },
  };
}

describe("PushDevicesCard — hàng thiết bị", () => {
  it("đăng ký chưa có lastSeenAt không in 'Invalid Date'", async () => {
    const view = await renderCard([
      { id: "d1", platform: "WEB", deviceLabel: "Chrome · Linux", createdAt: "2026-10-01T00:00:00Z" },
    ]);
    expect(view.text()).toContain("Chrome · Linux");
    expect(view.text()).not.toContain("push.devices.lastSeen");
    expect(view.text()).not.toContain("Invalid Date");
    view.unmount();
  });

  it("đăng ký có lastSeenAt vẫn hiện ngày thấy gần nhất", async () => {
    const view = await renderCard([
      {
        id: "d2",
        platform: "WEB",
        deviceLabel: "Firefox · Linux",
        lastSeenAt: "2026-10-05T00:00:00Z",
        createdAt: "2026-10-01T00:00:00Z",
      },
    ]);
    expect(view.text()).toContain("push.devices.lastSeen");
    view.unmount();
  });
});
