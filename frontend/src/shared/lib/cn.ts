import { clsx, type ClassValue } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

/**
 * tailwind-merge mặc định chỉ biết cỡ chữ `text-xs…9xl`; các token của dự án
 * (`text-body`, `text-caption`…) bị coi là MÀU chữ nên khi ghép với `text-text-*`
 * thì cỡ chữ bị xoá (rơi về 16px). Khai chúng vào nhóm `font-size`.
 */
const twMerge = extendTailwindMerge({
  extend: {
    classGroups: {
      "font-size": [{ text: ["display", "h1", "h2", "h3", "body", "caption", "small", "overline"] }],
    },
  },
});

/** Gộp className an toàn (clsx + tailwind-merge để xử lý xung đột utility). */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
