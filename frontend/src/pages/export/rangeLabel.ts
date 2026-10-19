import { EXPORT_PRESET_DAYS, type ExportRangePreset } from "@/features/export";

const DAY_MS = 24 * 60 * 60 * 1000;

export function resolveExportRange(
  preset: ExportRangePreset,
  customFrom: string,
  customTo: string,
): { from: Date; to: Date } | null {
  if (preset === "CUSTOM") {
    const from = new Date(`${customFrom}T00:00:00`);
    const to = new Date(`${customTo}T00:00:00`);
    if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime())) return null;
    return { from, to };
  }
  const to = new Date();
  const from = new Date(to.getTime() - (EXPORT_PRESET_DAYS[preset] - 1) * DAY_MS);
  return { from, to };
}
