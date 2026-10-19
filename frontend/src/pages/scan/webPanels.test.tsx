import { act } from "react";
import { createRoot } from "react-dom/client";
import { describe, expect, it, vi } from "vitest";
import type { ScanResult } from "@/entities/scan-result";
import { ScanMetaCard } from "./webPanels";

vi.mock("@/features/scan/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "vi" },
  }),
}));

function render(result: ScanResult): string {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(<ScanMetaCard result={result} />);
  });
  const text = container.textContent;
  act(() => {
    root.unmount();
  });
  return text;
}

describe("ScanMetaCard", () => {
  it("lần quét chưa gắn bảng màu (server bỏ hẳn key `chartVersion`) không in dòng 'bảng màu vundefined'", () => {
    const fromServer = {
      scanId: "s1",
      scanRequestId: "r1",
      assignment: "ASSIGNED",
      capturedAt: "2026-10-10T01:00:00Z",
      status: "COMPLETED",
      classification: "IN_RANGE",
      bandCode: "IN_RANGE",
      chartIsPlaceholder: false,
      creditCharged: true,
      isTrial: false,
      qualityFlags: [],
      triggeredFlags: [],
    } as unknown as ScanResult;
    const text = render(fromServer);
    expect(text).not.toContain("meta.chart");
    expect(text).not.toContain("undefined");
  });

  it("lần quét có bảng màu vẫn hiện dòng bảng màu", () => {
    const withChart = {
      scanId: "s2",
      scanRequestId: "r2",
      assignment: "ASSIGNED",
      capturedAt: "2026-10-10T01:00:00Z",
      status: "COMPLETED",
      classification: "IN_RANGE",
      bandCode: "IN_RANGE",
      chartVersion: 3,
      chartIsPlaceholder: false,
      creditCharged: true,
      isTrial: false,
      qualityFlags: [],
      triggeredFlags: [],
    } as unknown as ScanResult;
    expect(render(withChart)).toContain('meta.chartValue{"version":3}');
  });
});
