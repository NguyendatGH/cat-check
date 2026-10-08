import { forwardRef, type HTMLAttributes } from "react";
import { cn } from "../cn";
import { badgeVariants, type BadgeVariantProps } from "./Badge.variants";

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement>, BadgeVariantProps {}

/** Badge — tone neutral/brand/success. */
export const Badge = forwardRef<HTMLSpanElement, BadgeProps>(({ className, tone, ...props }, ref) => {
  return <span ref={ref} className={cn(badgeVariants({ tone }), className)} {...props} />;
});

Badge.displayName = "Badge";
