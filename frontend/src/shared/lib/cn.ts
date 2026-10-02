import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/** Gộp className an toàn (clsx + tailwind-merge để xử lý xung đột utility). */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
