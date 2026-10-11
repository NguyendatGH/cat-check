import { act } from "react";
import { createRoot } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";
import type { CatTrendsResponse } from "@/features/trends";
import { WebTrendsScreen } from "./webTrends";

/**
 * D13 bỏ hẳn key `confidence` ở lần quét không có độ tin cậy (và `phValue` ở lần không kết luận).
 * Bộ lọc `c !== null` cho `undefined` lọt vào phép trung bình ⇒ "NaN%"; hàng nhật ký nhân
 * `undefined * 100` ⇒ thanh rộng "NaN%".
 */
vi.mock("@/features/trends/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "vi" },
  }),
}));
// Recharts cần kích thước thật của container; test này không kiểm biểu đồ.
vi.mock("@/features/trends", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/trends")>()),
  PhTrendChart: () => null,
}));

function render(data: CatTrendsResponse): string {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <MemoryRouter>
        <WebTrendsScreen
          catId="c1"
          catName="Mướp"
          onExport={() => undefined}
          bands={[]}
          data={data}
          isPending={false}
          locked={false}
          isError={false}
          range="30D"
          onRangeChange={() => undefined}
          onRetry={() => undefined}
        />
      </MemoryRouter>,
    );
  });
  const text = container.textContent;
  act(() => {
    root.unmount();
  });
  return text;
}

describe("WebTrendsScreen", () => {
  it("điểm không có confidence (server bỏ key) không làm trung bình độ tin cậy thành NaN và không vẽ thanh NaN%", () => {
    const fromServer = {
      range: "30D",
      from: "2026-09-10T00:00:00Z",
      to: "2026-10-10T00:00:00Z",
      points: [
        {
          capturedAt: "2026-10-09T01:00:00Z",
          phValue: 6.5,
          classification: "IN_RANGE",
          nearBoundary: false,
          disputed: false,
        },
        {
          capturedAt: "2026-10-10T01:00:00Z",
          phValue: 6.4,
          classification: "IN_RANGE",
          confidence: 0.8,
          nearBoundary: false,
          disputed: false,
        },
      ],
      stats: { count: 2, inRangeCount: 2, lowConfidenceCount: 0 },
      bands: [],
      chartVersions: [],
    } as unknown as CatTrendsResponse;
    const text = render(fromServer);
    expect(text).not.toContain("NaN");
    expect(text).toContain('web.chartConfidenceReal{"percent":80}');
  });
});
