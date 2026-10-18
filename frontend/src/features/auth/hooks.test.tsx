import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useRequestOtp } from "./hooks";

vi.mock("@/shared/api/acceptLanguage", () => ({ currentAcceptLanguage: () => "en" }));

const fetchMock = vi.fn();

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  document.cookie = "XSRF-TOKEN=test-token";
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("useRequestOtp", () => {
  it("gửi kèm ngôn ngữ đang dùng để email OTP trước đăng ký đúng ngôn ngữ", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 202 }));
    const box: { current: ReturnType<typeof useRequestOtp> | undefined } = { current: undefined };
    function Probe() {
      box.current = useRequestOtp();
      return null;
    }
    const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
    const container = document.createElement("div");
    const root = createRoot(container);
    await act(async () => {
      root.render(
        <QueryClientProvider client={client}>
          <Probe />
        </QueryClientProvider>,
      );
    });

    await act(async () => {
      await box.current?.mutateAsync({ email: "a@x.vn", purpose: "REGISTER_VERIFY" });
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, { body: string }];
    expect(url).toBe("/api/v1/auth/otp/request");
    expect(JSON.parse(init.body)).toEqual({ email: "a@x.vn", purpose: "REGISTER_VERIFY", locale: "en" });
    act(() => {
      root.unmount();
    });
  });
});
