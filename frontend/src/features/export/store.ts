import { create } from "zustand";
import { localIsoDate } from "@/shared/lib/format/locale";
import { EXPORT_SECTIONS, type ExportRangePreset, type ExportSection, type ExportWizardStep } from "./types";


interface ExportWizardStore {
  step: ExportWizardStep;
  catId: string | null;
  catName: string | null;
  rangePreset: ExportRangePreset;
  customFrom: string;
  customTo: string;
  /** True once the user picked custom dates; until then the defaults are refreshed on use. */
  customRangeEdited: boolean;
  sections: ExportSection[];
  setStep: (step: ExportWizardStep) => void;
  setCat: (cat: { id: string; name: string }) => void;
  setRangePreset: (preset: ExportRangePreset) => void;
  setCustomRange: (from: string, to: string) => void;
  toggleSection: (section: ExportSection) => void;
  reset: () => void;
}

/** Default custom range: last 30 days ending today, in LOCAL time, computed when needed (not frozen at load). */
function defaultCustomRange(): { customFrom: string; customTo: string } {
  const now = new Date();
  return { customFrom: localIsoDate(now, -29), customTo: localIsoDate(now) };
}

export const useExportWizardStore = create<ExportWizardStore>((set) => ({
  step: 1,
  catId: null,
  catName: null,
  rangePreset: "30D",
  ...defaultCustomRange(),
  customRangeEdited: false,
  sections: [...EXPORT_SECTIONS],
  setStep: (step) => {
    set({ step });
  },
  setCat: (cat) => {
    set({ catId: cat.id, catName: cat.name });
  },
  setRangePreset: (rangePreset) => {
    set((state) =>
      rangePreset === "CUSTOM" && !state.customRangeEdited
        ? { rangePreset, ...defaultCustomRange() }
        : { rangePreset },
    );
  },
  setCustomRange: (customFrom, customTo) => {
    set({ customFrom, customTo, customRangeEdited: true });
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
      ...defaultCustomRange(),
      customRangeEdited: false,
      sections: [...EXPORT_SECTIONS],
    });
  },
}));
