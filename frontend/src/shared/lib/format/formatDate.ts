import { format } from "date-fns";
import { dateFnsLocale } from "./locale";

export function formatDate(date: string | number | Date, pattern = "dd/MM/yyyy"): string {
  return format(new Date(date), pattern, { locale: dateFnsLocale() });
}
