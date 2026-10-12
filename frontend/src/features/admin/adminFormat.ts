import { formatDate } from "@/shared/lib/format/formatDate";
import { formatNumber } from "@/shared/lib/format/formatNumber";


export const EMPTY_CELL = "—";

export function formatAdminDateTime(value: string | null | undefined): string {
  if (value === null || value === undefined || value === "") {
    return EMPTY_CELL;
  }
  const time = Date.parse(value);
  return Number.isNaN(time) ? EMPTY_CELL : formatDate(time, "dd/MM/yyyy HH:mm");
}

export function formatAdminNumber(value: number | null | undefined, options?: Intl.NumberFormatOptions): string {
  return value === null || value === undefined || Number.isNaN(value) ? EMPTY_CELL : formatNumber(value, options);
}

export function shortId(value: string | null | undefined, chars = 8): string {
  if (value === null || value === undefined || value === "") {
    return EMPTY_CELL;
  }
  return value.length <= chars ? value : `${value.slice(0, chars)}…`;
}
