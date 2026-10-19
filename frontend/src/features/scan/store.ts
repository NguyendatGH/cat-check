import { create } from "zustand";


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
