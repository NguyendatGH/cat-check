import { act } from "react";
import { createRoot } from "react-dom/client";
import { describe, expect, it, vi } from "vitest";
import { CatHistoryPage } from "./CatHistoryPage";

const data = vi.hoisted((): { summary?: unknown; history?: unknown; summaryArgs?: unknown[] } => ({}));

vi.mock("@/features/history/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/export/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/scan/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/history", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/history")>()),
  useCat: () => ({ data: { id: "c1", name: "Mướp", sex: "FEMALE" } }),
  useScanSummary: (...args: unknown[]) => {
    data.summaryArgs = args;
    return { data: data.summary };
  },
  useScanHistory: () => ({
    data: data.history,
    isPending: false,
    isError: false,
    refetch: vi.fn(),
    fetchNextPage: vi.fn(),
    hasNextPage: false,
    isFetchingNextPage: false,
  }),
}));
vi.mock("@/entities/ph-bands", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/entities/ph-bands")>()),
  usePhBands: () => ({ data: [] }),
}));
vi.mock("react-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router")>()),
  useParams: () => ({ catId: "c1" }),
  useNavigate: () => vi.fn(),
  Link: ({ children }: { children: React.ReactNode }) => <a href="/stub">{children}</a>,
}));
vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "vi" },
  }),
}));

describe("CatHistoryPage", () => {
  it("cat chỉ có lần quét không đo được pH (không median, không confidence) vẫn dựng được và không in NaN/undefined", () => {
    data.summary = {
      count: 1,
      byClassification: { INCONCLUSIVE: 1 },
      inconclusiveCount: 0,
      lowConfidenceCount: 0,
    };
    data.history = {
      pages: [
        {
          items: [
            {
              scanId: "s1",
              capturedAt: "2026-10-10T01:00:00Z",
              classification: "IN_RANGE",
              bandCode: "IN_RANGE",
              nearBoundary: false,
              imageAvailable: false,
              disputed: false,
              hasNote: false,
            },
          ],
          hasMore: false,
        },
      ],
      pageParams: [null],
    };
    (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    const container = document.createElement("div");
    const root = createRoot(container);
    act(() => {
      root.render(<CatHistoryPage />);
    });
    const text = container.textContent;
    act(() => {
      root.unmount();
    });
    expect(text).not.toContain("NaN");
    expect(text).not.toContain("undefined");
    expect(text).not.toContain("item.confidence");
    expect(text).not.toContain("web.confidence");
    expect(text).not.toContain("spectrum.average");
  });

  it("KPI tóm tắt dùng cùng khoảng thời gian với danh sách (mặc định 3 ngày)", () => {
    data.summary = undefined;
    data.history = { pages: [{ items: [], hasMore: false }], pageParams: [null] };
    (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    const container = document.createElement("div");
    const root = createRoot(container);
    act(() => {
      root.render(<CatHistoryPage />);
    });
    act(() => {
      root.unmount();
    });
    const expectedFrom = new Date();
    expectedFrom.setHours(0, 0, 0, 0);
    expectedFrom.setDate(expectedFrom.getDate() - 2);
    expect(data.summaryArgs).toEqual(["c1", expectedFrom.toISOString(), undefined]);
  });
});
