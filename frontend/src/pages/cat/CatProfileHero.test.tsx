import { act } from "react";
import { createRoot } from "react-dom/client";
import { describe, expect, it, vi } from "vitest";
import type { Cat } from "@/entities/cat";
import { CatProfileHero } from "./CatProfileHero";

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "vi" },
  }),
}));

function render(cat: Cat): string {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(<CatProfileHero cat={cat} />);
  });
  const text = container.textContent;
  act(() => {
    root.unmount();
  });
  return text;
}

describe("CatProfileHero", () => {
  it("bé chưa nhập cân nặng (server bỏ hẳn key `weightKg`) không in cân nặng 'undefined'", () => {
    // JSON thật của `GET /cats/{id}` khi hồ sơ chưa có cân nặng/giống/ảnh: các key đó không tồn tại.
    const fromServer = {
      id: "c1",
      publicCode: "CC-1",
      name: "Mướp",
      sex: "FEMALE",
      status: "ACTIVE",
      isPrimary: true,
      createdAt: "2026-10-01T00:00:00Z",
      updatedAt: "2026-10-01T00:00:00Z",
    } as unknown as Cat;
    const text = render(fromServer);
    expect(text).not.toContain("detail.weightLabel");
    expect(text).not.toContain("undefined");
  });

  it("bé có cân nặng vẫn hiện cân nặng", () => {
    const withWeight = {
      id: "c2",
      publicCode: "CC-2",
      name: "Mun",
      sex: "MALE",
      status: "ACTIVE",
      isPrimary: false,
      weightKg: 4.2,
      createdAt: "2026-10-01T00:00:00Z",
      updatedAt: "2026-10-01T00:00:00Z",
    } as unknown as Cat;
    expect(render(withWeight)).toContain('detail.weightLabel{"weight":4.2}');
  });
});
