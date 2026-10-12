import { act } from "react";
import { createRoot } from "react-dom/client";
import { describe, expect, it, vi } from "vitest";
import type { ExportJob } from "@/features/export";
import { ExportJobPage } from "./ExportJobPage";

const state = vi.hoisted((): { job?: unknown } => ({}));

vi.mock("@/features/export/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/export", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/export")>()),
  useExportJob: () => ({ data: state.job, isPending: false, isError: false, refetch: vi.fn() }),
}));
vi.mock("react-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router")>()),
  useParams: () => ({ jobId: "j1" }),
  useNavigate: () => vi.fn(),
}));
vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) => (options ? `${key}${JSON.stringify(options)}` : key),
  }),
}));

function render(job: unknown): string {
  state.job = job;
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(<ExportJobPage />);
  });
  const text = container.textContent;
  act(() => {
    root.unmount();
  });
  return text;
}

describe("ExportJobPage", () => {
  it("job READY thiếu pageCount/scanCount (server bỏ key) không in nhãn số trang", () => {
    const fromServer = { jobId: "j1", status: "READY", catId: "c1", documentCode: "CC-1" } as unknown as ExportJob;
    expect(render(fromServer)).not.toContain("job.pageCountLabel");
  });

  it("job READY có đủ số trang/lượt quét thì in nhãn", () => {
    const withCounts = {
      jobId: "j2",
      status: "READY",
      catId: "c1",
      documentCode: "CC-2",
      pageCount: 4,
      scanCount: 12,
    } as unknown as ExportJob;
    expect(render(withCounts)).toContain('job.pageCountLabel{"pages":4,"scans":12}');
  });
});
