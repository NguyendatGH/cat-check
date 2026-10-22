import { act } from "react";
import { createRoot } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/shared/api";
import { CareTipPage } from "./CareTipPage";

const state = vi.hoisted(() => ({ query: {}, refetch: vi.fn() }));

vi.mock("./hooks", () => ({
  useCareTip: () => ({ isLoading: false, isError: false, data: undefined, refetch: state.refetch, ...state.query }),
}));
vi.mock("react-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router")>()),
  useParams: () => ({ slug: "uong-nuoc" }),
}));
vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "en" },
  }),
}));

function render(query: Record<string, unknown>): HTMLDivElement {
  state.query = query;
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  act(() => {
    createRoot(container).render(
      <MemoryRouter>
        <CareTipPage />
      </MemoryRouter>,
    );
  });
  return container;
}

afterEach(() => {
  vi.clearAllMocks();
});

describe("CareTipPage", () => {
  it("404 → trạng thái không tìm thấy, không có nút thử lại", () => {
    const container = render({ isError: true, error: new ApiError("not found", 404) });
    expect(container.textContent).toContain("careTip.notFoundTitle");
    expect(container.querySelector("button")).toBeNull();
  });

  it("lỗi khác (5xx/mạng) → ErrorState với nút thử lại gọi refetch", () => {
    const container = render({ isError: true, error: new ApiError("boom", 503) });
    expect(container.textContent).toContain("document.loadError");
    expect(container.textContent).not.toContain("careTip.notFoundTitle");
    act(() => {
      container.querySelector("button")?.click();
    });
    expect(state.refetch).toHaveBeenCalledTimes(1);
  });

  it("thành công → hiển thị tiêu đề, ngày duyệt theo ngôn ngữ đang dùng", () => {
    const reviewedAt = "2026-03-04T12:00:00Z";
    const container = render({
      data: {
        slug: "uong-nuoc",
        title: "Uống đủ nước",
        summary: null,
        bodyMd: null,
        sourceReference: null,
        reviewedAt,
        publishedAt: null,
      },
    });
    expect(container.querySelector("h1")?.textContent).toBe("Uống đủ nước");
    expect(container.textContent).toContain(new Date(reviewedAt).toLocaleDateString("en"));
  });
});
