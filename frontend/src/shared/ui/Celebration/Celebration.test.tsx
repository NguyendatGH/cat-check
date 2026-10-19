import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { celebrate, useCelebrationStore } from "@/shared/lib/motion/celebration";
import { CelebrationHost } from "./Celebration";
import { readBrandPalette } from "./fireworks";

function mockReducedMotion(reduced: boolean) {
  vi.stubGlobal(
    "matchMedia",
    (query: string) =>
      ({
        matches: reduced && query.includes("reduce"),
        media: query,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
      }) as unknown as MediaQueryList,
  );
}

describe("CelebrationHost", () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(null);
    useCelebrationStore.getState().reset();
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => {
      root.unmount();
    });
    container.remove();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("hiện pháo hoa + lời chào, không chặn thao tác, tự tắt sau thời lượng", () => {
    mockReducedMotion(false);
    act(() => {
      root.render(<CelebrationHost />);
    });
    expect(container.querySelector("[data-testid='celebration-overlay']")).toBeNull();

    act(() => {
      celebrate({ title: "Chào", message: "Quà chào mừng", durationMs: 3000 });
    });
    const overlay = container.querySelector("[data-testid='celebration-overlay']");
    expect(overlay).not.toBeNull();
    expect(overlay?.className).toContain("pointer-events-none");
    expect(container.querySelector("canvas")).not.toBeNull();
    expect(container.textContent).toContain("Quà chào mừng");

    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(container.querySelector("[data-testid='celebration-overlay']")).toBeNull();
    expect(useCelebrationStore.getState().current).toBeNull();
  });

  it("prefers-reduced-motion: không có canvas, chỉ lời chào", () => {
    mockReducedMotion(true);
    act(() => {
      root.render(<CelebrationHost />);
      celebrate({ message: "Quà chào mừng" });
    });
    expect(container.querySelector("canvas")).toBeNull();
    expect(container.querySelector("[role='status']")?.textContent).toContain("Quà chào mừng");
  });

  it("unmount giữa chừng không để lại timer", () => {
    mockReducedMotion(false);
    act(() => {
      root.render(<CelebrationHost />);
      celebrate();
    });
    act(() => {
      root.render(<></>);
    });
    expect(vi.getTimerCount()).toBe(0);
  });
});

describe("readBrandPalette", () => {
  it("lấy màu từ token CSS, rơi về màu thương hiệu khi chưa có token", () => {
    document.documentElement.style.setProperty("--color-primary", "rgb(1, 2, 3)");
    const palette = readBrandPalette();
    expect(palette[0]).toBe("rgb(1, 2, 3)");
    expect(palette).toHaveLength(5);
    expect(palette.every((c) => c.length > 0)).toBe(true);
    document.documentElement.style.removeProperty("--color-primary");
  });
});
