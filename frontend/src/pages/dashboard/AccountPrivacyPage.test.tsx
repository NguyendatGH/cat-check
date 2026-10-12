import { act } from "react";
import { createRoot } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { AccountPrivacyPage } from "./AccountPrivacyPage";

const hooks = vi.hoisted((): { data: Record<string, unknown> } => ({ data: {} }));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "vi" },
  }),
}));
vi.mock("@/features/privacy", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/privacy")>();
  const idle = {
    isPending: false,
    isError: false,
    isSuccess: false,
    data: undefined,
    error: null,
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    reset: vi.fn(),
  };
  const patched: Record<string, unknown> = { ...actual };
  for (const [name, value] of Object.entries(actual)) {
    if (/^use[A-Z]/.test(name) && typeof value === "function") {
      patched[name] = () => (name in hooks.data ? { ...idle, data: hooks.data[name] } : idle);
    }
  }
  return patched;
});

describe("AccountPrivacyPage — lịch sử đồng ý", () => {
  it("dòng không có policyVersion hiện 'không rõ phiên bản', không tạo liên kết /legal/privacy/v/undefined", () => {
    hooks.data.useConsentPurposes = [];
    hooks.data.useCurrentConsents = [];
    hooks.data.useDataInventory = [];
    hooks.data.useDsarRequests = [];
    hooks.data.useAccessLog = [];
    hooks.data.useConsentHistory = [
      {
        purposeCode: "HEALTH_REMINDER_PUSH",
        status: "GRANTED",
        method: "CHECKBOX",
        occurredAt: "2026-10-01T00:00:00Z",
      },
      {
        purposeCode: "ANALYTICS",
        status: "GRANTED",
        policyVersion: "1.2",
        method: "CHECKBOX",
        occurredAt: "2026-10-02T00:00:00Z",
      },
    ];
    (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    const container = document.createElement("div");
    const root = createRoot(container);
    act(() => {
      root.render(
        <MemoryRouter>
          <AccountPrivacyPage />
        </MemoryRouter>,
      );
    });
    const hrefs = Array.from(container.querySelectorAll("a")).map((anchor) => anchor.getAttribute("href"));
    const text = container.textContent;
    act(() => {
      root.unmount();
    });
    expect(hrefs).not.toContain("/legal/privacy/v/undefined");
    expect(hrefs).toContain("/legal/privacy/v/1.2");
    expect(text).toContain("privacyCenter.history.policyUnknown");
  });
});
