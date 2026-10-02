import { formatDistanceToNow } from "date-fns";
import { vi } from "date-fns/locale";

/** Format thời gian tương đối, ví dụ "3 giờ trước". */
export function formatRelative(date: string | number | Date): string {
  return formatDistanceToNow(new Date(date), { addSuffix: true, locale: vi });
}
