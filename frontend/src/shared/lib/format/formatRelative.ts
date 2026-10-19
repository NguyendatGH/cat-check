import { formatDistanceToNow } from "date-fns";
import { dateFnsLocale } from "./locale";

export function formatRelative(date: string | number | Date): string {
  return formatDistanceToNow(new Date(date), { addSuffix: true, locale: dateFnsLocale() });
}
