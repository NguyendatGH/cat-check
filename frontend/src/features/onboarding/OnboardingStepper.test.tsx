import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { OnboardingStepper } from "./components";

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "vi" },
  }),
}));

let mounted: { root: Root; container: HTMLElement } | null = null;

afterEach(() => {
  if (mounted) {
    act(() => {
      mounted?.root.unmount();
    });
    mounted.container.remove();
    mounted = null;
  }
});

async function render(current: 1 | 2 | 3 | 4 | 5): Promise<HTMLElement> {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  document.body.append(container);
  const root = createRoot(container);
  await act(async () => {
    root.render(<OnboardingStepper current={current} />);
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
  mounted = { root, container };
  return container;
}

describe("OnboardingStepper optional steps", () => {
  it("chỉ đánh dấu tuỳ chọn cho bước Sức khoẻ (2) và Kích hoạt (4)", async () => {
    const container = await render(1);
    const tags = Array.from(container.querySelectorAll("span")).filter(
      (node) => node.textContent === "stepper.optionalTag",
    );
    expect(tags).toHaveLength(2);
    expect(container.textContent).toContain("stepper.optionalHint");
  });

  it("nhãn bước hiện tại trên mobile nêu rõ tuỳ chọn khi ở bước Sức khoẻ", async () => {
    const container = await render(2);
    expect(container.textContent).toContain("(stepper.optionalTag)");
  });

  it("không ghi tuỳ chọn cho bước bắt buộc Miễn trừ y tế", async () => {
    const container = await render(3);
    expect(container.textContent).not.toContain("(stepper.optionalTag)");
  });
});
