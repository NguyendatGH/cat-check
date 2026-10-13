import { cva, type VariantProps } from "class-variance-authority";

export const skeletonVariants = cva("animate-pulse bg-chip-bg", {
  variants: {
    shape: {
      text: "h-4 w-full rounded-sm",
      card: "h-32 w-full rounded-lg",
      circle: "size-10 rounded-full",
    },
  },
  defaultVariants: { shape: "text" },
});

export type SkeletonVariantProps = VariantProps<typeof skeletonVariants>;
