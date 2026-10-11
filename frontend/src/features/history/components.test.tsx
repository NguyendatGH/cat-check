import { act } from "react";
import { createRoot } from "react-dom/client";
import { describe, expect, it, vi } from "vitest";
import type { ScanListItem } from "@/entities/scan-result";
import { ScanTimelineItem } from "./components";

vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
    i18n: { language: "vi" },
  }),
}));

function render(scan: ScanListItem): string {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(
      <ScanTimelineItem
        scan={scan}
        bands={[]}
        timestampLabel="Hôm nay"
        disputedLabel="Đang khiếu nại"
        detailLabel="Chi tiết"
        onOpen={() => undefined}
      />,
    );
  });
  const text = container.textContent;
  act(() => {
    root.unmount();
  });
  return text;
}

describe("ScanTimelineItem", () => {
  it("lần quét không có độ tin cậy (server bỏ hẳn key `confidence`) không in chip NaN", () => {
    // JSON thật từ `GET /scans`: `confidence`/`phValue`/`confidenceBand` null ⇒ không có key.
    const fromServer = {
      scanId: "s1",
      capturedAt: "2026-10-10T01:00:00Z",
      classification: "IN_RANGE",
      bandCode: "IN_RANGE",
      nearBoundary: false,
      imageAvailable: false,
      disputed: false,
      hasNote: false,
    } as unknown as ScanListItem;
    const text = render(fromServer);
    expect(text).not.toContain("NaN");
    expect(text).not.toContain("item.confidence");
  });

  it("lần quét có độ tin cậy vẫn hiện chip phần trăm", () => {
    const withConfidence = {
      scanId: "s2",
      capturedAt: "2026-10-10T01:00:00Z",
      classification: "IN_RANGE",
      bandCode: "IN_RANGE",
      confidence: 0.83,
      nearBoundary: false,
      imageAvailable: false,
      disputed: false,
      hasNote: false,
    } as unknown as ScanListItem;
    expect(render(withConfidence)).toContain('item.confidence{"value":83}');
  });
});
