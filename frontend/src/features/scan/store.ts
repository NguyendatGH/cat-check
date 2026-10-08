import { create } from "zustand";

/**
 * Store zustand cho luồng chụp quét — session-only (KHÔNG persist localStorage): giữ file ảnh +
 * mèo đã chọn giữa 2 bước điều hướng (`/scan/select-cat` → `/scan`) trong 1 phiên chụp.
 *
 * Quyết định thứ tự luồng (khác mockup gốc `05. Chọn Mèo` vốn đặt bước chọn mèo SAU khi có kết
 * quả — xem `docs/handovers/A6.md`): hợp đồng BE TD-02 (`POST /scans`) đòi `catId`/`assignment`
 * NGAY TRONG request đầu tiên (một bước: phân tích + lưu + trừ credit cùng transaction), nên
 * việc chọn mèo BẮT BUỘC xảy ra TRƯỚC khi gọi camera, không phải sau. Đổi mèo SAU khi đã có kết
 * quả dùng route riêng `/scan/:scanId/reassign-cat` (E9, ≤24h/≤3 lần) — không dùng store này.
 */

export type CaptureAssignment = "ASSIGNED" | "SHARED_UNKNOWN";

interface ScanCaptureStore {
  scanRequestId: string;
  selectedCatId: string | null;
  selectedCatName: string | null;
  assignment: CaptureAssignment | null;
  file: File | null;
  previewUrl: string | null;
  setSelectedCat: (cat: { id: string; name: string } | null) => void;
  setSharedUnknown: () => void;
  setFile: (file: File | null) => void;
  resetCapture: () => void;
  startNewRequest: () => void;
  /** Lượt thử MỚI cho cùng bé/khay đang chọn (sau INCONCLUSIVE hoặc "Quét lần mới"): đổi `scanRequestId`, bỏ ảnh cũ. */
  startNewAttempt: () => void;
}

function newRequestId(): string {
  return crypto.randomUUID();
}

export const useScanCaptureStore = create<ScanCaptureStore>((set, get) => ({
  scanRequestId: newRequestId(),
  selectedCatId: null,
  selectedCatName: null,
  assignment: null,
  file: null,
  previewUrl: null,
  setSelectedCat: (cat) => {
    set({
      selectedCatId: cat?.id ?? null,
      selectedCatName: cat?.name ?? null,
      assignment: cat ? "ASSIGNED" : null,
    });
  },
  setSharedUnknown: () => {
    set({ selectedCatId: null, selectedCatName: null, assignment: "SHARED_UNKNOWN" });
  },
  setFile: (file) => {
    const prev = get().previewUrl;
    if (prev) URL.revokeObjectURL(prev);
    set({ file, previewUrl: file ? URL.createObjectURL(file) : null });
  },
  resetCapture: () => {
    const prev = get().previewUrl;
    if (prev) URL.revokeObjectURL(prev);
    set({ file: null, previewUrl: null });
  },
  startNewAttempt: () => {
    const prev = get().previewUrl;
    if (prev) URL.revokeObjectURL(prev);
    set({ scanRequestId: newRequestId(), file: null, previewUrl: null });
  },
  startNewRequest: () => {
    const prev = get().previewUrl;
    if (prev) URL.revokeObjectURL(prev);
    set({
      scanRequestId: newRequestId(),
      selectedCatId: null,
      selectedCatName: null,
      assignment: null,
      file: null,
      previewUrl: null,
    });
  },
}));
