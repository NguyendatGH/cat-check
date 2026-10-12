import { formatDistanceToNow } from "date-fns";
import { vi } from "date-fns/locale";

export function formatRelative(date: string | number | Date): string {
  return formatDistanceToNow(new Date(date), { addSuffix: true, locale: vi });
}
