import { cva, type VariantProps } from "class-variance-authority";

export const badgeVariants = cva("inline-flex items-center rounded-full px-2.5 py-0.5 text-small font-semibold", {
  variants: {
    tone: {
      neutral: "bg-chip-bg text-text-secondary",
      brand: "bg-primary text-white",
      success: "bg-success-bg text-success-text",
    },
  },
  defaultVariants: { tone: "neutral" },
});

export type BadgeVariantProps = VariantProps<typeof badgeVariants>;
