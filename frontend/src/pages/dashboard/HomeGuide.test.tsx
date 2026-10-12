import { act } from "react";
import { createRoot } from "react-dom/client";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { HOME_GUIDE_STORAGE_KEY, HomeGuide } from "./HomeGuide";

vi.mock("react-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router")>()),
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => <a href={to}>{children}</a>,
}));

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}:${JSON.stringify(options)}` : key),
  }),
}));

describe("HomeGuide", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("renders 3 roadmap steps when not dismissed", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(<HomeGuide catName="Miu" />);
    });

    expect(container.textContent).toContain("web.success.roadmap.title");
    expect(container.textContent).toContain("web.success.roadmap.step1.title");
    expect(container.textContent).toContain("web.success.roadmap.step2.title");
    expect(container.textContent).toContain("web.success.roadmap.step3.title");
    expect(container.textContent).toContain("dashboard.startScan");

    act(() => {
      root.unmount();
    });
    container.remove();
  });

  it("does not render when dismissed in localStorage", () => {
    localStorage.setItem(HOME_GUIDE_STORAGE_KEY, "true");

    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    act(() => {
      root.render(<HomeGuide catName="Miu" />);
    });

    expect(container.textContent).toBe("");

    act(() => {
      root.unmount();
    });
    container.remove();
  });
});
