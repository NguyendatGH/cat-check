import { cva, type VariantProps } from "class-variance-authority";

/**
 * `text-overline` CHÍNH LÀ 12px/16px/600 (p10 §2.2 "Overline/Badge") — trước đây tổ hợp
 * `text-small font-semibold` dựng lại đúng token đó bằng tay, trong khi `small` theo spec là
 * weight 400 dành cho chữ pháp lý.
 *
 * Mọi tone đều là cặp `*-bg` + `*-text`: p10 §10.1 tính ra chữ trắng trên nền đặc
 * `success`/`warning` chỉ đạt 2.28:1 / 2.15:1 — fail AA. Ba tone `warning`/`danger`/`info`
 * có sẵn ở đây để call site không phải tự chế `bg-warning text-white`.
 */
export const badgeVariants = cva("inline-flex items-center rounded-full px-2.5 py-0.5 text-overline", {
  variants: {
    tone: {
      neutral: "bg-chip-bg text-text-secondary",
      brand: "bg-primary text-white",
      success: "bg-success-bg text-success-text",
      warning: "bg-warning-bg text-warning-text",
      danger: "bg-danger-bg text-danger-text",
      info: "bg-info text-info-text",
    },
  },
  defaultVariants: { tone: "neutral" },
});

export type BadgeVariantProps = VariantProps<typeof badgeVariants>;
