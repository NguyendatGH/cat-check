import { act } from "react";
import { createRoot } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { useScanCaptureStore } from "@/features/scan";
import { ScanPage } from "./ScanPage";

const { mutate } = vi.hoisted(() => ({ mutate: vi.fn() }));

vi.mock("@/features/scan/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/credit/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/history/mocks", () => ({ handlers: [], worker: {} }));
vi.mock("@/features/scan", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/scan")>()),
  useSubmitScan: () => ({ mutate, reset: vi.fn(), isPending: false, isSuccess: false, isError: false }),
  useRecentCatScans: () => ({ data: undefined }),
  useScanByRequest: () => ({ data: undefined, errorUpdateCount: 0 }),
}));
vi.mock("@/features/credit", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/credit")>()),
  useCreditBalance: () => ({ data: { availableBalance: 3, trialScansRemaining: 0 } }),
  useEntitlement: () => ({ data: undefined }),
}));
vi.mock("@/features/history", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/history")>()),
  useCat: () => ({ data: undefined }),
}));
vi.mock("@/entities/ph-bands", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/entities/ph-bands")>()),
  usePhBands: () => ({ data: [] }),
}));
vi.mock("@/shared/lib/hooks/useBreakpoint", () => ({ useBreakpoint: () => false }));
vi.mock("react-i18next", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-i18next")>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: "vi" } }),
}));

describe("ScanPage", () => {
  it("gửi phân tích ngay khi chọn ảnh, không còn bước xác nhận", () => {
    (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    URL.createObjectURL = () => "blob:preview";
    URL.revokeObjectURL = () => undefined;
    useScanCaptureStore.getState().setSelectedCat({ id: "cat-1", name: "Mun" });
    const container = document.createElement("div");
    const root = createRoot(container);
    act(() => {
      root.render(
        <MemoryRouter>
          <ScanPage />
        </MemoryRouter>,
      );
    });

    const file = new File(["x"], "litter.jpg", { type: "image/jpeg" });
    const gallery = container.querySelector<HTMLInputElement>('input[type="file"]:not([capture])');
    if (!gallery) throw new Error("gallery input missing");
    Object.defineProperty(gallery, "files", { value: [file] });
    act(() => {
      gallery.dispatchEvent(new Event("change", { bubbles: true }));
    });

    expect(mutate).toHaveBeenCalledTimes(1);
    expect(mutate).toHaveBeenCalledWith({
      file,
      metadata: expect.objectContaining({
        scanRequestId: useScanCaptureStore.getState().scanRequestId,
        catId: "cat-1",
        assignment: "ASSIGNED",
        captureSource: "GALLERY",
      }) as unknown,
    });
    expect(container.textContent).not.toContain("capture.submit");
    act(() => {
      root.unmount();
    });
  });
});
