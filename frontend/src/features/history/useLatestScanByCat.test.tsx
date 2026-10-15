import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ScanListItem } from "@/entities/scan-result";
import { useLatestScanByCat } from "./hooks";

const fetchScanHistory = vi.hoisted(() => vi.fn());
vi.mock("./api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api")>()),
  fetchScanHistory,
}));

function scan(scanId: string, catId: string): ScanListItem {
  return {
    scanId,
    catId,
    catName: catId,
    capturedAt: "2026-10-01T00:00:00Z",
    phValue: 6.5,
    classification: "IN_RANGE",
    bandCode: "IN_RANGE",
    confidence: null,
    confidenceBand: null,
    nearBoundary: false,
    thumbnailHex: null,
    imageAvailable: false,
    disputed: false,
    hasNote: false,
  };
}

let mounted: Root | undefined;
let result = new Map<string, ScanListItem>();

function Probe(props: { cats: { id: string; lastScanAt: string | null }[]; recent: ScanListItem[] }) {
  result = useLatestScanByCat(props.cats, props.recent);
  return null;
}

async function render(node: React.ReactElement) {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const root = createRoot(document.createElement("div"));
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
    await Promise.resolve();
  });
  await act(async () => {
    await new Promise((r) => setTimeout(r, 0));
  });
  mounted = root;
}

afterEach(() => {
  act(() => {
    mounted?.unmount();
  });
  fetchScanHistory.mockReset();
});

describe("useLatestScanByCat", () => {
  it("tra riêng lần quét mới nhất của bé không có trong trang lịch sử gần đây", async () => {
    fetchScanHistory.mockResolvedValue({ items: [scan("old-b", "b")], hasMore: false, nextCursor: null });
    await render(
      <Probe
        cats={[
          { id: "a", lastScanAt: "2026-10-10T00:00:00Z" },
          { id: "b", lastScanAt: "2026-01-01T00:00:00Z" },
          { id: "c", lastScanAt: null },
        ]}
        recent={[scan("a2", "a"), scan("a1", "a")]}
      />,
    );
    expect(result.get("a")?.scanId).toBe("a2");
    expect(result.get("b")?.scanId).toBe("old-b");
    expect(result.has("c")).toBe(false);
    expect(fetchScanHistory).toHaveBeenCalledTimes(1);
    expect(fetchScanHistory).toHaveBeenCalledWith(expect.objectContaining({ catId: "b", limit: 1 }));
  });
});
