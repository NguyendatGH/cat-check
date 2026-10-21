import { act } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ClinicalSignsDialog } from "./CatDetailPage";

vi.mock("@/features/cat/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/history/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/scan/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/auth/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/credit/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/export/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/onboarding/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/trends/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: { returnObjects?: boolean }) => (options?.returnObjects ? [] : key),
    i18n: { language: "vi" },
  }),
}));

const fetchMock = vi.fn();

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  document.body.innerHTML = "";
});

async function submitSigns(serverBody: unknown): Promise<string> {
  fetchMock.mockResolvedValue(
    new Response(JSON.stringify(serverBody), { status: 201, headers: { "Content-Type": "application/json" } }),
  );
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <ClinicalSignsDialog catId="cat-1" open onOpenChange={() => undefined} />
      </QueryClientProvider>,
    );
    await Promise.resolve();
  });

  const checkboxes = Array.from(document.body.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
  act(() => {
    checkboxes[0].click();
  });
  act(() => {
    checkboxes[checkboxes.length - 1].click();
  });
  const submit = Array.from(document.body.querySelectorAll("button")).find(
    (button) => button.textContent === "clinicalSigns.form.submit",
  );
  await act(async () => {
    submit?.click();
    await new Promise((resolve) => setTimeout(resolve, 150));
  });
  const text = document.body.textContent;
  act(() => {
    root.unmount();
  });
  return text;
}

describe("ClinicalSignsDialog — kết quả khai dấu hiệu", () => {
  it("server không kích hoạt cảnh báo (không có key triggeredFlag) ⇒ hiện kết quả 'ổn', không hiện khối khẩn cấp", async () => {
    const text = await submitSigns({ reportId: "r1" });
    expect(text).toContain("clinicalSigns.result.okTitle");
    expect(text).not.toContain("clinicalSigns.result.triggeredTitle");
  });

  it("server kích hoạt cảnh báo ⇒ hiện kết quả 'đã kích hoạt'", async () => {
    const text = await submitSigns({
      reportId: "r2",
      triggeredFlag: { flagId: "f1", ruleCode: "R1", severity: "URGENT" },
    });
    expect(text).toContain("clinicalSigns.result.triggeredTitle");
  });
});
