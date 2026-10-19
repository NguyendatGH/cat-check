import { formatNumber } from "./formatNumber";

export function formatPh(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "—";
  }
  return formatNumber(value, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
