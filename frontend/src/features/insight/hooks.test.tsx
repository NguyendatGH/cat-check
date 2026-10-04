import { act, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useAcknowledgeHealthFlag, useHealthFlag, useHealthFlags } from "./hooks";
import type { HealthFlagListResponse, HealthFlagView } from "./types";

/**
 * Nhóm G của p8 §8.4.7. Test khoá vào đúng ba điều dễ gãy khi nối API thật:
 *  1. URL + query string (lọc `catId`/`acknowledged` sai là lấy nhầm cảnh báo của bé khác);
 *  2. `acknowledge` phải POST đúng `/health-flags/{id}/acknowledge` và chịu được 204 rỗng;
 *  3. sau khi xác nhận phải invalidate cả `['cat']` — `unacknowledgedFlagCount` nằm trong
 *     `GET /cats`, không tự giảm theo.
 *
 * Dựng hook bằng `react-dom/client` + `act` thay vì `@testing-library/react`: package đó có
 * trong devDependencies nhưng peer `@testing-library/dom` KHÔNG được cài, và brief W1-E cấm
 * thêm gói mới.
 */

const FLAG: HealthFlagView = {
  flagId: "0199aa00-0000-7000-8000-000000000001",
  catId: "0199bb00-0000-7000-8000-000000000002",
  ruleCode: "REPEATED_OUT_OF_RANGE",
  severity: "ATTENTION",
  triggerScanId: null,
  triggeredAt: "2026-09-01T03:00:00Z",
  windowFrom: "2026-08-30T03:00:00Z",
  windowTo: "2026-09-01T03:00:00Z",
  messageKey: "rule.REPEATED_OUT_OF_RANGE.message",
  messageParams: { count: 2 },
  explanationVi: "Hai lần quét gần nhau cùng lệch về phía kiềm.",
  acknowledgedAt: null,
  acknowledged: false,
  disclaimerKey: "legal.disclaimer.short",
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function newClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
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

describe("useHealthFlags — G1", () => {
  it("gọi GET /api/v1/health-flags không kèm query khi không lọc", async () => {
    const body: HealthFlagListResponse = { items: [FLAG], hasMore: false, nextCursor: null };
    fetchMock.mockResolvedValue(jsonResponse(body));

    const hook = await renderHook(() => useHealthFlags(), newClient());
    await waitUntil(() => hook.current.isSuccess, "query thành công");

    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/v1/health-flags");
    expect(hook.current.data?.items).toHaveLength(1);
    hook.unmount();
  });

  it("đưa catId/acknowledged/severity/limit vào query string", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ items: [], hasMore: false, nextCursor: null }));

    const hook = await renderHook(
      () => useHealthFlags({ catId: FLAG.catId, acknowledged: false, severity: "URGENT", limit: 5 }),
      newClient(),
    );
    await waitUntil(() => hook.current.isSuccess, "query thành công");

    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url.startsWith("/api/v1/health-flags?")).toBe(true);
    const params = new URLSearchParams(url.split("?")[1]);
    expect(params.get("catId")).toBe(FLAG.catId);
    expect(params.get("acknowledged")).toBe("false");
    expect(params.get("severity")).toBe("URGENT");
    expect(params.get("limit")).toBe("5");
    hook.unmount();
  });

  it("không gọi API khi bị tắt (khách chưa đăng nhập)", async () => {
    const hook = await renderHook(() => useHealthFlags({}, false), newClient());
    expect(fetchMock).not.toHaveBeenCalled();
    expect(hook.current.fetchStatus).toBe("idle");
    hook.unmount();
  });

  it("ném ApiError mang errorCode của ProblemDetail khi lỗi", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ detail: "Khong co quyen", errorCode: "FORBIDDEN" }, 403));

    const hook = await renderHook(() => useHealthFlags(), newClient());
    await waitUntil(() => hook.current.isError, "query lỗi");

    const error = hook.current.error as { status?: number; code?: string } | null;
    expect(error?.status).toBe(403);
    expect(error?.code).toBe("FORBIDDEN");
    hook.unmount();
  });
});

describe("useHealthFlag — G2", () => {
  it("không gọi gì khi chưa có flagId", async () => {
    const hook = await renderHook(() => useHealthFlag(undefined), newClient());
    expect(fetchMock).not.toHaveBeenCalled();
    hook.unmount();
  });

  it("gọi GET /api/v1/health-flags/{id} khi có flagId", async () => {
    fetchMock.mockResolvedValue(jsonResponse(FLAG));

    const hook = await renderHook(() => useHealthFlag(FLAG.flagId), newClient());
    await waitUntil(() => hook.current.isSuccess, "query thành công");

    expect(fetchMock.mock.calls[0]?.[0]).toBe(`/api/v1/health-flags/${FLAG.flagId}`);
    expect(hook.current.data?.ruleCode).toBe("REPEATED_OUT_OF_RANGE");
    hook.unmount();
  });
});

describe("useAcknowledgeHealthFlag — G3", () => {
  it("POST đúng endpoint, chịu được 204 rỗng và làm mới cache cat", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const client = newClient();
    const invalidate = vi.spyOn(client, "invalidateQueries");

    const hook = await renderHook(() => useAcknowledgeHealthFlag(), client);
    act(() => {
      hook.current.mutate(FLAG.flagId);
    });
    await waitUntil(() => hook.current.isSuccess, "mutation thành công");

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`/api/v1/health-flags/${FLAG.flagId}/acknowledge`);
    expect(init.method).toBe("POST");

    const invalidatedKeys = invalidate.mock.calls.map((call) => JSON.stringify(call[0]?.queryKey));
    expect(invalidatedKeys).toContain(JSON.stringify(["insight"]));
    expect(invalidatedKeys).toContain(JSON.stringify(["cat"]));
    hook.unmount();
  });
});
