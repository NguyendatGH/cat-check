import { act } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SettingsSecurityPage } from "./SettingsSecurityPage";

/**
 * Khoá cache của trạng thái TOTP là `["auth", "totp-status"]` (`useTotpStatus`). Trang từng
 * invalidate `["auth", "totp"]` — khoá đó KHÔNG là tiền tố của `totp-status` (so khớp theo phần
 * tử mảng), nên sau khi tắt/bật 2FA nhãn trạng thái đứng im ở giá trị cũ.
 */
vi.mock("@/features/auth/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ t: (key: string) => key }),
}));

const server = { totpStatus: "ACTIVE" as "ACTIVE" | "NONE" };
const fetchMock = vi.fn((input: string, init?: RequestInit) => {
  const method = init?.method ?? "GET";
  const json = (body: unknown) =>
    Promise.resolve(new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } }));
  if (input.endsWith("/account/mfa/totp") && method === "DELETE") {
    server.totpStatus = "NONE";
    return Promise.resolve(new Response(null, { status: 204 }));
  }
  if (input.endsWith("/account/mfa/totp")) {
    return json(
      server.totpStatus === "ACTIVE"
        ? { status: "ACTIVE", activatedAt: "2026-10-01T00:00:00Z", recoveryCodesRemaining: 9 }
        : { status: "NONE", recoveryCodesRemaining: 0 },
    );
  }
  if (input.endsWith("/auth/sessions")) return json({ items: [] });
  return Promise.resolve(new Response("{}", { status: 404 }));
});

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  server.totpStatus = "ACTIVE";
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

async function settle(): Promise<void> {
  for (let i = 0; i < 6; i += 1) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 5));
    });
  }
}

describe("SettingsSecurityPage — TOTP", () => {
  it("tắt 2FA xong thì nhãn trạng thái chuyển sang 'chưa bật' (làm mới đúng khoá totp-status)", async () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    await act(async () => {
      root.render(
        <QueryClientProvider client={client}>
          <SettingsSecurityPage />
        </QueryClientProvider>,
      );
      await Promise.resolve();
    });
    await settle();
    expect(container.textContent).toContain("security.mfaStatusActive");

    const disable = Array.from(container.querySelectorAll("button")).find(
      (button) => button.textContent === "security.mfaDisableCta",
    );
    expect(disable).toBeDefined();
    await act(async () => {
      disable?.click();
      await Promise.resolve();
    });
    await settle();

    expect(container.textContent).toContain("security.mfaStatusNone");
    expect(container.textContent).not.toContain("security.mfaStatusActive");
    act(() => {
      root.unmount();
    });
    container.remove();
  });
});
