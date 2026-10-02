import { create } from "zustand";
import { EXPORT_SECTIONS, type ExportRangePreset, type ExportSection, type ExportWizardStep } from "./types";

/**
 * Store zustand cho wizard export 3 bước (chọn mèo → khoảng thời gian → mục nội dung), khớp
 * mockup `10. Xuất Hồ sơ Sức khỏe`. Session-only — không persist (dữ liệu tạm giữa các bước).
 */

interface ExportWizardStore {
  step: ExportWizardStep;
  catId: string | null;
  catName: string | null;
  rangePreset: ExportRangePreset;
  customFrom: string;
  customTo: string;
  sections: ExportSection[];
  setStep: (step: ExportWizardStep) => void;
  setCat: (cat: { id: string; name: string }) => void;
  setRangePreset: (preset: ExportRangePreset) => void;
  setCustomRange: (from: string, to: string) => void;
  toggleSection: (section: ExportSection) => void;
  reset: () => void;
}

const today = new Date().toISOString().slice(0, 10);
const thirtyDaysAgo = new Date(Date.now() - 29 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

export const useExportWizardStore = create<ExportWizardStore>((set) => ({
  step: 1,
  catId: null,
  catName: null,
  rangePreset: "30D",
  customFrom: thirtyDaysAgo,
  customTo: today,
  sections: [...EXPORT_SECTIONS],
  setStep: (step) => {
    set({ step });
  },
  setCat: (cat) => {
    set({ catId: cat.id, catName: cat.name });
  },
  setRangePreset: (rangePreset) => {
    set({ rangePreset });
  },
  setCustomRange: (customFrom, customTo) => {
    set({ customFrom, customTo });
  },
  toggleSection: (section) => {
    set((state) => ({
      sections: state.sections.includes(section)
        ? state.sections.filter((s) => s !== section)
        : [...state.sections, section],
    }));
  },
  reset: () => {
    set({
      step: 1,
      catId: null,
      catName: null,
      rangePreset: "30D",
      customFrom: thirtyDaysAgo,
      customTo: today,
      sections: [...EXPORT_SECTIONS],
    });
  },
}));
