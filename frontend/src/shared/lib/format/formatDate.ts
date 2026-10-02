import { format } from "date-fns";
import { vi } from "date-fns/locale";

/** Format ngày theo locale tiếng Việt, mặc định dd/MM/yyyy. */
export function formatDate(date: string | number | Date, pattern = "dd/MM/yyyy"): string {
  return format(new Date(date), pattern, { locale: vi });
}
