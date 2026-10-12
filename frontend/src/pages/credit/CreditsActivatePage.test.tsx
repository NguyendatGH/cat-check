import { act } from "react";
import { createRoot } from "react-dom/client";
import { describe, expect, it, vi } from "vitest";
import { CreditsActivatePage } from "./CreditsActivatePage";

vi.mock("@/features/credit/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/credit", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/credit")>();
  return {
    ...actual,
    useCreditBalance: () => ({
      data: {
        availableBalance: 12,
        trialScansRemaining: 0,
        batches: [],
      },
    }),
    useActivateCode: () => ({
      mutateAsync: vi.fn(),
      isPending: false,
    }),
  };
});

vi.mock("react-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router")>()),
  useNavigate: () => vi.fn(),
}));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}:${JSON.stringify(options)}` : key),
  }),
}));

describe("CreditsActivatePage", () => {
  it("renders mobile hero header, balance indicator, activation code input, and guide", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(<CreditsActivatePage />);
    });

    expect(container.textContent).toContain("pages.activate.title");
    expect(container.textContent).toContain("activate.currentBalance");
    expect(container.textContent).toContain("12 balance.unit");
    expect(container.textContent).toContain("activate.guideTitle");
    expect(container.textContent).toContain("activate.perksTitle");

    act(() => {
      root.unmount();
    });
    container.remove();
  });
});
