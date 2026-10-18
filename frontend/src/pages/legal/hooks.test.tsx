import { act, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { usePolicyCurrent, usePolicyVersions } from "./hooks";
import type { PolicyView } from "./types";

const lang = vi.hoisted(() => ({ current: "vi" }));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ i18n: { language: lang.current } }),
}));

const POLICY: PolicyView = {
  policyType: "TERMS",
  version: "1.0",
  locale: "vi",
  title: "Điều khoản sử dụng",
  contentMd: "# Điều khoản sử dụng",
  contentUrl: null,
  effectiveFrom: "2026-01-01T00:00:00Z",
  requiresReconsent: false,
  summaryOfChanges: null,
  affectedPurposes: [],
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function notFound(): Response {
  return jsonResponse({ detail: "Không tìm thấy", errorCode: "POLICY_VERSION_NOT_FOUND" }, 404);
}

function newClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

interface HookHandle<T> {
  readonly current: T;
  rerender: () => Promise<void>;
  unmount: () => void;
}

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
  const render = () =>
    act(async () => {
      root.render(
        <Tree>
          <Probe />
        </Tree>,
      );
      await Promise.resolve();
    });
  await render();
  return {
    get current() {
      return box.current as T;
    },
    rerender: render,
    unmount: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
    },
  };
}

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
  lang.current = "vi";
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("usePolicyCurrent", () => {
  it("gửi locale theo ngôn ngữ đang dùng", async () => {
    lang.current = "en";
    fetchMock.mockResolvedValue(jsonResponse({ ...POLICY, locale: "en", title: "Terms of Use" }));

    const hook = await renderHook(() => usePolicyCurrent("TERMS"), newClient());
    await waitUntil(() => hook.current.isSuccess, "query thành công");

    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/v1/policies/TERMS?locale=en");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    hook.unmount();
  });

  it("đổi ngôn ngữ thì tải lại bằng locale mới", async () => {
    fetchMock.mockResolvedValue(jsonResponse(POLICY));

    const hook = await renderHook(() => usePolicyCurrent("TERMS"), newClient());
    await waitUntil(() => hook.current.isSuccess, "query vi");
    lang.current = "en";
    await hook.rerender();
    await waitUntil(() => fetchMock.mock.calls.length === 2, "query en");

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      "/api/v1/policies/TERMS?locale=vi",
      "/api/v1/policies/TERMS?locale=en",
    ]);
    hook.unmount();
  });

  it("chưa có bản en (POLICY_VERSION_NOT_FOUND) thì lùi về bản vi", async () => {
    lang.current = "en";
    fetchMock.mockResolvedValueOnce(notFound()).mockResolvedValueOnce(jsonResponse(POLICY));

    const hook = await renderHook(() => usePolicyCurrent("TERMS"), newClient());
    await waitUntil(() => hook.current.isSuccess, "query thành công");

    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      "/api/v1/policies/TERMS?locale=en",
      "/api/v1/policies/TERMS?locale=vi",
    ]);
    expect(hook.current.data?.locale).toBe("vi");
    hook.unmount();
  });

  it("bản vi không có thì báo lỗi, không gọi lại", async () => {
    fetchMock.mockResolvedValue(notFound());

    const hook = await renderHook(() => usePolicyCurrent("TERMS"), newClient());
    await waitUntil(() => hook.current.isError, "query lỗi");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    hook.unmount();
  });

  it("lỗi khác 404 không lùi về vi", async () => {
    lang.current = "en";
    fetchMock.mockResolvedValue(jsonResponse({ detail: "Lỗi máy chủ", errorCode: "INTERNAL_ERROR" }, 500));

    const hook = await renderHook(() => usePolicyCurrent("TERMS"), newClient());
    await waitUntil(() => hook.current.isError, "query lỗi");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    hook.unmount();
  });
});

describe("usePolicyVersions", () => {
  it("gửi locale theo ngôn ngữ đang dùng", async () => {
    lang.current = "en";
    fetchMock.mockResolvedValue(jsonResponse({ policyCode: "TERMS", items: [] }));

    const hook = await renderHook(() => usePolicyVersions("TERMS"), newClient());
    await waitUntil(() => hook.current.isSuccess, "query thành công");

    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/v1/policies/TERMS/versions?locale=en");
    hook.unmount();
  });
});
