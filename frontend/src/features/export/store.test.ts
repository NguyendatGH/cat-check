import { afterEach, describe, expect, it, vi } from "vitest";
import { useExportWizardStore } from "./store";

describe("export wizard default custom range", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("is computed in local time when (re)used, not frozen at module load", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 9, 15, 0, 30));
    useExportWizardStore.getState().reset();
    expect(useExportWizardStore.getState().customTo).toBe("2026-10-15");
    expect(useExportWizardStore.getState().customFrom).toBe("2026-09-16");

    vi.setSystemTime(new Date(2026, 9, 16, 8, 0));
    useExportWizardStore.getState().setRangePreset("CUSTOM");
    expect(useExportWizardStore.getState().customTo).toBe("2026-10-16");
  });

  it("keeps user-picked custom dates", () => {
    useExportWizardStore.getState().reset();
    useExportWizardStore.getState().setCustomRange("2026-01-01", "2026-01-10");
    useExportWizardStore.getState().setRangePreset("7D");
    useExportWizardStore.getState().setRangePreset("CUSTOM");
    expect(useExportWizardStore.getState().customFrom).toBe("2026-01-01");
  });
});
