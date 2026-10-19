import { clsx, type ClassValue } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

const mergeWithTokens = extendTailwindMerge({
  extend: {
    theme: {
      text: ["display", "h1", "h2", "h3", "body", "caption", "small", "overline"],
    },
  },
});

export function cn(...inputs: ClassValue[]): string {
  return mergeWithTokens(clsx(inputs));
}
