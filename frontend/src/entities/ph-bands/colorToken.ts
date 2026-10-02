/**
 * Ánh xạ `colorToken` (chuỗi trả về từ API, VD `color-ph-normal`) sang class Tailwind.
 *
 * p6 §6.7.1 bắt buộc component nghiệp vụ pH dùng ĐÚNG 4 token vai trò `color-ph-*`
 * (KHÔNG gọi thẳng `color-success`/`color-warning`/`color-danger`).
 *
 * Dùng cú pháp "arbitrary value" (`bg-[var(--color-ph-normal-bg)]`) chứ không phải utility
 * rút gọn `bg-ph-normal-bg`. Trước đây là vì `--color-ph-*` chỉ khai ở `:root`, không có
 * trong `@theme`, nên utility rút gọn KHÔNG được sinh ra; nay đã bổ sung vào `@theme` và cả
 * hai dạng đều chạy. Vẫn giữ dạng arbitrary ở đây vì `color-ph-unknown` cần fallback
 * `var(x, y)` — thứ mà utility rút gọn không diễn đạt được. KHÔNG hard-code hex, KHÔNG đổi
 * sang token semantic khác.
 *
 * Class ở đây là CHUỖI TĨNH (không nội suy runtime) để công cụ quét nội dung của Tailwind
 * nhận diện được — tra theo khoá qua object, không dựng chuỗi động.
 */

export interface PhTokenStyle {
  /** Nền nhạt (badge, banner). */
  bg: string;
  /** Chữ tương phản với `bg`. */
  text: string;
  /** Viền/chấm đậm (dot, border, thanh gauge). */
  solid: string;
}

const PH_TOKEN_STYLES: Record<string, PhTokenStyle> = {
  "color-ph-normal": {
    bg: "bg-[var(--color-ph-normal-bg)]",
    text: "text-[var(--color-ph-normal-text)]",
    solid: "bg-[var(--color-ph-normal)]",
  },
  "color-ph-mild": {
    bg: "bg-[var(--color-ph-mild-bg)]",
    text: "text-[var(--color-ph-mild-text)]",
    solid: "bg-[var(--color-ph-mild)]",
  },
  "color-ph-abnormal": {
    bg: "bg-[var(--color-ph-abnormal-bg)]",
    text: "text-[var(--color-ph-abnormal-text)]",
    solid: "bg-[var(--color-ph-abnormal)]",
  },
  "color-ph-unknown": {
    // `--color-ph-unknown-text` chưa được W3 khai báo trong index.css — dùng fallback CSS
    // var(...,...) ngay trong arbitrary value thay vì chép hex hay đổi sang token khác.
    bg: "bg-[var(--color-ph-unknown-bg)]",
    text: "text-[var(--color-ph-unknown-text,var(--color-text-secondary))]",
    solid: "bg-[var(--color-ph-unknown)]",
  },
};

const FALLBACK_STYLE = PH_TOKEN_STYLES["color-ph-unknown"];

/** Tra style theo `colorToken` trả về từ API — token lạ/chưa biết thì rơi về `unknown`. */
export function phTokenStyle(colorToken: string): PhTokenStyle {
  return PH_TOKEN_STYLES[colorToken] ?? FALLBACK_STYLE;
}
