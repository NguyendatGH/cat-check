import { act } from "react";
import { createRoot } from "react-dom/client";
import { MemoryRouter } from "react-router";
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
    usePackageCatalog: () => ({
      isPending: false,
      isError: false,
      data: [
        {
          code: "PLUS",
          name: "CATCHECK Plus",
          weightKg: 3,
          creditAmount: 10,
          creditValidityDays: 7,
          maxCatProfiles: 1,
          historyLevel: "ADVANCED",
          hasTrend: true,
          hasReminder: true,
          hasExport: true,
          storeImage: true,
        },
      ],
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
      root.render(
        <MemoryRouter initialEntries={["/credits/activate"]}>
          <CreditsActivatePage />
        </MemoryRouter>,
      );
    });

    expect(container.textContent).toContain("pages.activate.title");
    expect(container.textContent).toContain("activate.currentBalance");
    expect(container.textContent).toContain("12 balance.unit");
    expect(container.textContent).toContain("activate.guideTitle");
    expect(container.textContent).toContain("activate.perksTitle");
    expect(container.textContent).toContain("CATCHECK Plus");
    expect(container.textContent).toContain("activate.qrNoticeTitle");
    expect(container.textContent).toContain("activate.qrNotice1kg");
    expect(container.textContent).toContain("activate.qrNotice25kg");
    expect(container.textContent).toContain("activate.qrScanCta");
    expect(container.textContent).not.toContain("activate.qrSamplesTitle");
    expect(container.querySelector<HTMLInputElement>("#activation-code-field")?.value).toBe("");

    act(() => {
      root.unmount();
    });
    container.remove();
  });

  it.each([
    ["cc-plus-7k3m9qx2rt", "CC-PLUS-7K3M9QX2RT"],
    ["not-a-code", ""],
  ])("prefills the code from ?code=%s", (param, expected) => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(
        <MemoryRouter initialEntries={[`/credits/activate?code=${param}`]}>
          <CreditsActivatePage />
        </MemoryRouter>,
      );
    });

    expect(container.querySelector<HTMLInputElement>("#activation-code-field")?.value).toBe(expected);

    act(() => {
      root.unmount();
    });
    container.remove();
  });
});
