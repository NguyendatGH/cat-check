import { clsx, type ClassValue } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

/**
 * `cn` riêng cho `shared/ui` — biết các token cỡ chữ của `@theme` (`text-caption`, `text-h3`…).
 *
 * Vì sao cần: twMerge mặc định chỉ biết cỡ chữ của Tailwind gốc (`text-sm`, `text-lg`…), nên
 * xếp `text-caption`/`text-overline`/`text-h3` vào nhóm MÀU chữ. Hệ quả đo được: `Badge`
 * (`text-overline` + `text-success-text`) mất cỡ 12px, tiêu đề `Dialog`/`Sheet` (`text-h3` +
 * `text-text-primary`) mất cỡ 20px, `Tabs` mất 14px, còn `Button` secondary/tertiary thì ngược
 * lại — giữ cỡ chữ nhưng MẤT màu chữ của variant.
 *
 * ponytail: gốc lỗi nằm ở `shared/lib/cn.ts` (dùng chung toàn app, ngoài phạm vi gói việc này)
 * — sửa ở đó bằng đúng cấu hình bên dưới thì xoá file này và trả các component về `cn` chung.
 */
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
